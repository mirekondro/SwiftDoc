package dk.easv.swiftdoc.model;

public class User {

    public enum Role { USER, ADMIN }

    private final int userId;
    private final String username;
    private final Role role;
    private final boolean active;

    public User(int userId, String username, Role role, boolean active) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.active = active;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public boolean isAdmin() { return role == Role.ADMIN; }
    public boolean isActive() { return active; }

    @Override
    public String toString() {
        return username + " (" + role + (active ? "" : ", inactive") + ")";
    }
}
