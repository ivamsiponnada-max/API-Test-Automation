package com.sdet.framework.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Request payload for creating or updating a user.
 *
 * <p>Null fields are omitted when serialised, so the same model serves full (PUT/POST)
 * and partial (PATCH) updates.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"name", "email", "gender", "status"})
public class UserRequest {

    private String name;
    private String email;
    private String gender;
    private String status;

    /** Required by Jackson. */
    public UserRequest() {
    }

    private UserRequest(Builder builder) {
        this.name = builder.name;
        this.email = builder.email;
        this.gender = builder.gender;
        this.status = builder.status;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Copies this request into a builder so tests can derive variants without mutating the original. */
    public Builder toBuilder() {
        return new Builder().name(name).email(email).gender(gender).status(status);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserRequest that)) {
            return false;
        }
        return Objects.equals(name, that.name)
                && Objects.equals(email, that.email)
                && Objects.equals(gender, that.gender)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, gender, status);
    }

    @Override
    public String toString() {
        return "UserRequest{name='" + name + "', email='" + email + "', gender='" + gender + "', status='" + status + "'}";
    }

    public static final class Builder {
        private String name;
        private String email;
        private String gender;
        private String status;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder gender(String gender) {
            this.gender = gender;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public UserRequest build() {
            return new UserRequest(this);
        }
    }
}
