package com.lenerd46.spotifyplus;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class SpotifyUser {
    private static final Gson GSON = new Gson();

    @SerializedName("account_id")
    public final String accountId;

    public final String country;

    @SerializedName("display_name")
    public final String displayName;

    public final String email;

    @SerializedName("explicit_content")
    public final ExplicitContentSettings explicitContent;

    @SerializedName("external_urls")
    public final Map<String, String> externalUrls;

    public final Followers followers;
    public final String href;

    public final String id;

    public final List<Image> images;
    public final String product;
    public final String type;
    public final String uri;
    public final Integer color;
    public final Boolean verified;
    public final String pronouns;
    public final String location;
    public final Boolean kid;
    public final String socialHandle;

    private SpotifyUser(String accountId, String country, String displayName, String email, ExplicitContentSettings explicitContent, Map<String, String> externalUrls, Followers followers, String href, String id, List<Image> images, String product, String type, String uri, Integer color, Boolean verified, String pronouns, String location, Boolean kid, String socialHandle) {
        this.accountId = accountId;
        this.country = country;
        this.displayName = displayName;
        this.email = email;
        this.explicitContent = explicitContent;
        this.externalUrls = externalUrls == null ? Collections.emptyMap() : Collections.unmodifiableMap(externalUrls);
        this.followers = followers;
        this.href = href;
        this.id = id;
        this.images = images == null ? Collections.emptyList() : Collections.unmodifiableList(images);
        this.product = product;
        this.type = type;
        this.uri = uri;
        this.color = color;
        this.verified = verified;
        this.pronouns = pronouns;
        this.location = location;
        this.kid = kid;
        this.socialHandle = socialHandle;
    }

    static SpotifyUser fromJson(String json) {
        SpotifyUser parsed = GSON.fromJson(json, SpotifyUser.class);
        if (parsed == null || parsed.id == null || parsed.id.isBlank()) {
            throw new IllegalArgumentException("Spotify returned a user without an id");
        }

        return new SpotifyUser(parsed.accountId, parsed.country, parsed.displayName, parsed.email,
                parsed.explicitContent, parsed.externalUrls, parsed.followers, parsed.href,
                parsed.id, parsed.images, parsed.product, parsed.type, parsed.uri, parsed.color,
                parsed.verified, parsed.pronouns, parsed.location, parsed.kid, parsed.socialHandle);
    }

    static SpotifyUser fromInternalProfile(byte[] protobuf, ClassLoader spotifyClassLoader) throws Exception {
        Class<?> profileClass = Class.forName("com.spotify.identity.proto.v3.Identity$UserProfile", true, spotifyClassLoader);
        Object parser = profileClass.getMethod("parser").invoke(null);
        Object profile = null;

        for (Method method : parser.getClass().getMethods()) {
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == byte[].class) {
                Object candidate = method.invoke(parser, protobuf);

                if (profileClass.isInstance(candidate)) {
                    profile = candidate;
                    break;
                }
            }
        }

        if (profile == null) throw new IllegalStateException("Could not find Spotify profile parser");

        String username = wrappedString(field(profile, "username_"));
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Spotify returned a profile without a username");
        }

        List<Image> images = new ArrayList<>();
        Object rawImages = field(profile, "images_");
        if (rawImages instanceof Iterable<?> iterable) {
            for (Object image : iterable) {
                images.add(new Image((Integer) field(image, "maxHeight_"), (String) field(image, "url_"), (Integer) field(image, "maxWidth_")));
            }
        }

        String displayName = wrappedString(field(profile, "name_"));
        return new SpotifyUser(
                wrappedString(field(profile, "accountId_")), null, displayName, null,
                null, null, null, null, username, images, null, "user",
                "spotify:user:" + username, (Integer) wrappedValue(field(profile, "color_")),
                (Boolean) wrappedValue(field(profile, "verified_")),
                wrappedString(field(profile, "pronouns_")),
                wrappedString(field(profile, "location_")),
                (Boolean) wrappedValue(field(profile, "isKid_")),
                wrappedString(field(profile, "socialHandle_")));
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static Object wrappedValue(Object wrapper) throws Exception {
        if (wrapper == null) return null;
        return wrapper.getClass().getMethod("getValue").invoke(wrapper);
    }

    private static String wrappedString(Object wrapper) throws Exception {
        return (String) wrappedValue(wrapper);
    }

    public Image getProfileImage() {
        return images.isEmpty() ? null : images.get(0);
    }

    public String getProfileImageUrl() {
        Image image = getProfileImage();
        return image == null ? null : image.url;
    }

    public String getCreditName() {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }

    public static final class ExplicitContentSettings {
        @SerializedName("filter_enabled")
        public final boolean filterEnabled;

        @SerializedName("filter_locked")
        public final boolean filterLocked;

        private ExplicitContentSettings(boolean filterEnabled, boolean filterLocked) {
            this.filterEnabled = filterEnabled;
            this.filterLocked = filterLocked;
        }
    }

    public static final class Followers {
        public final String href;
        public final int total;

        private Followers(String href, int total) {
            this.href = href;
            this.total = total;
        }
    }

    public static final class Image {
        public final Integer height;
        public final String url;
        public final Integer width;

        private Image(Integer height, String url, Integer width) {
            this.height = height;
            this.url = url;
            this.width = width;
        }
    }
}
