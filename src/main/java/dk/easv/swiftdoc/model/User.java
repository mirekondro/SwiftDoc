package dk.easv.swiftdoc.model;

public class User {

    public enum Role { USER, ADMIN }

    private final int userId;
    private final String username;
    private final Role role;

    public User(int userId, String username, Role role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public boolean isAdmin() { return role == Role.ADMIN; }

    @Override
    public String toString() {
        return username + " (" + role + ")";
    }
}
