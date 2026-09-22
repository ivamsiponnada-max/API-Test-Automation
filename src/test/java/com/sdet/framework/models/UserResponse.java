package com.sdet.framework.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * Response body for a user resource.
 *
 * <p>Unknown fields are ignored during deserialisation so the model stays resilient;
 * contract strictness is enforced separately by JSON Schema validation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String gender;
    private String status;

    public UserResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
        if (!(o instanceof UserResponse that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(email, that.email)
                && Objects.equals(gender, that.gender)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, email, gender, status);
    }

    @Override
    public String toString() {
        return "UserResponse{id=" + id + ", name='" + name + "', email='" + email
                + "', gender='" + gender + "', status='" + status + "'}";
    }
}
