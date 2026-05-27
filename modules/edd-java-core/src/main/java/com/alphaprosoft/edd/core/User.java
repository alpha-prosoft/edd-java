package com.alphaprosoft.edd.core;

import java.util.List;
import java.util.Map;

/**
 * The acting principal for a request. Mirrors edd-core's {@code :user}: a stable {@code id}, the
 * active {@code role}, all {@code roles} the user holds, an {@code email}, and free-form {@code
 * attrs}. The tenant ({@code realm}) is not part of the user — it lives on {@link RequestMeta},
 * matching edd-core.
 */
public record User(
    String id, String role, List<String> roles, String email, Map<String, Object> attrs) {

  public static final User ANONYMOUS = new User(null, "anonymous", List.of(), null, Map.of());

  public User {
    roles = roles == null ? List.of() : List.copyOf(roles);
    attrs = attrs == null ? Map.of() : Map.copyOf(attrs);
  }

  public static User of(String id, String role) {
    return new User(id, role, List.of(role), null, Map.of());
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(User existing) {
    return new Builder(existing);
  }

  public static final class Builder {

    private String id;
    private String role;
    private List<String> roles = List.of();
    private String email;
    private Map<String, Object> attrs = Map.of();

    private Builder() {}

    private Builder(User u) {
      this.id = u.id;
      this.role = u.role;
      this.roles = u.roles;
      this.email = u.email;
      this.attrs = u.attrs;
    }

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder role(String role) {
      this.role = role;
      return this;
    }

    public Builder roles(List<String> roles) {
      this.roles = roles;
      return this;
    }

    public Builder email(String email) {
      this.email = email;
      return this;
    }

    public Builder attrs(Map<String, Object> attrs) {
      this.attrs = attrs;
      return this;
    }

    public User build() {
      return new User(id, role, roles, email, attrs);
    }
  }
}
