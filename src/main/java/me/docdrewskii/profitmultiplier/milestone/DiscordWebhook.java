package me.docdrewskii.profitmultiplier.milestone;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.docdrewskii.profitmultiplier.ProfitMultiplier;
import me.docdrewskii.profitmultiplier.util.FoliaScheduler;
import org.bukkit.configuration.ConfigurationSection;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DiscordWebhook {

    private static final int FLAG_COMPONENTS_V2 = 1 << 15;
    private static final int TYPE_SECTION = 9;
    private static final int TYPE_TEXT_DISPLAY = 10;
    private static final int TYPE_THUMBNAIL = 11;
    private static final int TYPE_MEDIA_GALLERY = 12;
    private static final int TYPE_SEPARATOR = 14;
    private static final int TYPE_CONTAINER = 17;

    private static final class FieldSpec {
        final String name;
        final String value;
        final boolean inline;

        FieldSpec(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }
    }

    private static final class ComponentSpec {
        final String type;
        final String text;
        final String thumbnail;
        final String url;
        final boolean divider;
        final boolean largeSpacing;

        ComponentSpec(String type, String text, String thumbnail, String url,
                      boolean divider, boolean largeSpacing) {
            this.type = type;
            this.text = text;
            this.thumbnail = thumbnail;
            this.url = url;
            this.divider = divider;
            this.largeSpacing = largeSpacing;
        }
    }

    private final ProfitMultiplier plugin;
    private final String url;
    private final String username;
    private final String avatarUrl;
    private final boolean componentsV2;
    private final boolean maxTierOnly;

    private final String embedTitle;
    private final String embedDescription;
    private final Integer embedColor;
    private final String embedThumbnail;
    private final String embedFooter;
    private final boolean embedTimestamp;
    private final List<FieldSpec> embedFields;

    private final Integer accentColor;
    private final List<ComponentSpec> components;

    private DiscordWebhook(ProfitMultiplier plugin, String url, String username, String avatarUrl,
                           boolean componentsV2, boolean maxTierOnly,
                           String embedTitle, String embedDescription, Integer embedColor,
                           String embedThumbnail, String embedFooter, boolean embedTimestamp,
                           List<FieldSpec> embedFields, Integer accentColor, List<ComponentSpec> components) {
        this.plugin = plugin;
        this.url = url;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.componentsV2 = componentsV2;
        this.maxTierOnly = maxTierOnly;
        this.embedTitle = embedTitle;
        this.embedDescription = embedDescription;
        this.embedColor = embedColor;
        this.embedThumbnail = embedThumbnail;
        this.embedFooter = embedFooter;
        this.embedTimestamp = embedTimestamp;
        this.embedFields = embedFields;
        this.accentColor = accentColor;
        this.components = components;
    }

    public static DiscordWebhook parse(ProfitMultiplier plugin, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return null;
        }
        String url = section.getString("webhook-url", "").trim();
        if (!url.startsWith("http")) {
            plugin.getLogger().warning("milestones.discord is enabled but webhook-url is not set — webhook disabled.");
            return null;
        }

        String style = section.getString("style", "embed").toLowerCase(Locale.ROOT);
        boolean componentsV2 = style.equals("components-v2") || style.equals("componentsv2") || style.equals("v2");
        boolean maxTierOnly = !"every-tier".equalsIgnoreCase(section.getString("announce", "max-tier"));

        String embedTitle = "";
        String embedDescription = "";
        Integer embedColor = null;
        String embedThumbnail = "";
        String embedFooter = "";
        boolean embedTimestamp = true;
        List<FieldSpec> embedFields = new ArrayList<>();
        ConfigurationSection embed = section.getConfigurationSection("embed");
        if (embed != null) {
            embedTitle = embed.getString("title", "");
            embedDescription = embed.getString("description", "");
            embedColor = parseColor(embed.getString("color"));
            embedThumbnail = embed.getString("thumbnail", "");
            embedFooter = embed.getString("footer", "");
            embedTimestamp = embed.getBoolean("timestamp", true);
            for (Map<?, ?> field : embed.getMapList("fields")) {
                Object name = field.get("name");
                Object value = field.get("value");
                if (name == null || value == null) continue;
                Object inline = field.get("inline");
                embedFields.add(new FieldSpec(name.toString(), value.toString(),
                        inline != null && Boolean.parseBoolean(inline.toString())));
            }
        }

        Integer accentColor = null;
        List<ComponentSpec> components = new ArrayList<>();
        ConfigurationSection cv2 = section.getConfigurationSection("components-v2");
        if (cv2 != null) {
            accentColor = parseColor(cv2.getString("accent-color"));
            for (Map<?, ?> comp : cv2.getMapList("components")) {
                Object type = comp.get("type");
                if (type == null) continue;
                String typeName = type.toString().toLowerCase(Locale.ROOT);
                Object text = comp.get("text");
                Object thumbnail = comp.get("thumbnail");
                Object imageUrl = comp.get("url");
                Object divider = comp.get("divider");
                Object spacing = comp.get("spacing");
                components.add(new ComponentSpec(typeName,
                        text == null ? "" : text.toString(),
                        thumbnail == null ? "" : thumbnail.toString(),
                        imageUrl == null ? "" : imageUrl.toString(),
                        divider == null || Boolean.parseBoolean(divider.toString()),
                        spacing != null && "large".equalsIgnoreCase(spacing.toString())));
            }
        }

        if (componentsV2 && components.isEmpty()) {
            plugin.getLogger().warning("milestones.discord style is components-v2 but no components are defined — webhook disabled.");
            return null;
        }

        return new DiscordWebhook(plugin, url, section.getString("username", ""),
                section.getString("avatar-url", ""), componentsV2, maxTierOnly,
                embedTitle, embedDescription, embedColor, embedThumbnail, embedFooter,
                embedTimestamp, embedFields, accentColor, components);
    }

    public boolean isMaxTierOnly() {
        return maxTierOnly;
    }

    public void send(Map<String, String> placeholders) {
        String body = buildPayload(placeholders).toString();
        FoliaScheduler.runAsync(plugin, () -> post(body));
    }

    private JsonObject buildPayload(Map<String, String> placeholders) {
        JsonObject root = new JsonObject();
        if (!username.isEmpty()) {
            root.addProperty("username", MilestoneManager.apply(username, placeholders));
        }
        if (!avatarUrl.isEmpty()) {
            root.addProperty("avatar_url", MilestoneManager.apply(avatarUrl, placeholders));
        }
        if (componentsV2) {
            root.addProperty("flags", FLAG_COMPONENTS_V2);
            JsonObject container = new JsonObject();
            container.addProperty("type", TYPE_CONTAINER);
            if (accentColor != null) {
                container.addProperty("accent_color", accentColor);
            }
            JsonArray children = new JsonArray();
            for (ComponentSpec spec : components) {
                JsonObject component = buildComponent(spec, placeholders);
                if (component != null) children.add(component);
            }
            container.add("components", children);
            JsonArray topLevel = new JsonArray();
            topLevel.add(container);
            root.add("components", topLevel);
        } else {
            JsonArray embeds = new JsonArray();
            embeds.add(buildEmbed(placeholders));
            root.add("embeds", embeds);
        }
        return root;
    }

    private JsonObject buildEmbed(Map<String, String> placeholders) {
        JsonObject embed = new JsonObject();
        if (!embedTitle.isEmpty()) {
            embed.addProperty("title", MilestoneManager.apply(embedTitle, placeholders));
        }
        if (!embedDescription.isEmpty()) {
            embed.addProperty("description", MilestoneManager.apply(embedDescription, placeholders));
        }
        if (embedColor != null) {
            embed.addProperty("color", embedColor);
        }
        if (!embedThumbnail.isEmpty()) {
            JsonObject thumbnail = new JsonObject();
            thumbnail.addProperty("url", MilestoneManager.apply(embedThumbnail, placeholders));
            embed.add("thumbnail", thumbnail);
        }
        if (!embedFooter.isEmpty()) {
            JsonObject footer = new JsonObject();
            footer.addProperty("text", MilestoneManager.apply(embedFooter, placeholders));
            embed.add("footer", footer);
        }
        if (embedTimestamp) {
            embed.addProperty("timestamp", Instant.now().toString());
        }
        if (!embedFields.isEmpty()) {
            JsonArray fields = new JsonArray();
            for (FieldSpec spec : embedFields) {
                JsonObject field = new JsonObject();
                field.addProperty("name", MilestoneManager.apply(spec.name, placeholders));
                field.addProperty("value", MilestoneManager.apply(spec.value, placeholders));
                field.addProperty("inline", spec.inline);
                fields.add(field);
            }
            embed.add("fields", fields);
        }
        return embed;
    }

    private JsonObject buildComponent(ComponentSpec spec, Map<String, String> placeholders) {
        switch (spec.type) {
            case "text": {
                JsonObject text = new JsonObject();
                text.addProperty("type", TYPE_TEXT_DISPLAY);
                text.addProperty("content", MilestoneManager.apply(spec.text, placeholders));
                return text;
            }
            case "separator": {
                JsonObject separator = new JsonObject();
                separator.addProperty("type", TYPE_SEPARATOR);
                separator.addProperty("divider", spec.divider);
                separator.addProperty("spacing", spec.largeSpacing ? 2 : 1);
                return separator;
            }
            case "section": {
                JsonObject text = new JsonObject();
                text.addProperty("type", TYPE_TEXT_DISPLAY);
                text.addProperty("content", MilestoneManager.apply(spec.text, placeholders));
                if (spec.thumbnail.isEmpty()) {
                    return text;
                }
                JsonObject section = new JsonObject();
                section.addProperty("type", TYPE_SECTION);
                JsonArray children = new JsonArray();
                children.add(text);
                section.add("components", children);
                JsonObject media = new JsonObject();
                media.addProperty("url", MilestoneManager.apply(spec.thumbnail, placeholders));
                JsonObject accessory = new JsonObject();
                accessory.addProperty("type", TYPE_THUMBNAIL);
                accessory.add("media", media);
                section.add("accessory", accessory);
                return section;
            }
            case "image": {
                if (spec.url.isEmpty()) return null;
                JsonObject media = new JsonObject();
                media.addProperty("url", MilestoneManager.apply(spec.url, placeholders));
                JsonObject item = new JsonObject();
                item.add("media", media);
                JsonArray items = new JsonArray();
                items.add(item);
                JsonObject gallery = new JsonObject();
                gallery.addProperty("type", TYPE_MEDIA_GALLERY);
                gallery.add("items", items);
                return gallery;
            }
            default:
                plugin.getLogger().warning("Unknown discord component type '" + spec.type + "' — skipped.");
                return null;
        }
    }

    private void post(String body) {
        String target = url;
        if (componentsV2) {
            target += (url.indexOf('?') >= 0 ? "&" : "?") + "with_components=true";
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(target).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "ProfitMultiplier");
            connection.setDoOutput(true);
            OutputStream out = connection.getOutputStream();
            out.write(body.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                String error = readStream(connection.getErrorStream());
                plugin.getLogger().warning("Discord webhook returned HTTP " + code
                        + (error.isEmpty() ? "" : ": " + error));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Discord webhook failed: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readStream(InputStream in) {
        if (in == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1 && sb.length() < 500) {
                sb.append((char) c);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static Integer parseColor(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        boolean hex = false;
        if (value.startsWith("#")) {
            value = value.substring(1);
            hex = true;
        } else if (value.toLowerCase(Locale.ROOT).startsWith("0x")) {
            value = value.substring(2);
            hex = true;
        }
        try {
            return Integer.parseInt(value, hex || !value.matches("\\d+") ? 16 : 10);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
