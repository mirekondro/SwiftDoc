package dk.easv.swiftdoc.service;

import dk.easv.swiftdoc.dal.UserDAO;
import dk.easv.swiftdoc.dal.UserDAO.AuthRow;
import dk.easv.swiftdoc.model.User;
import dk.easv.swiftdoc.model.User.Role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AuthService {

    private final UserDAO userDAO = new UserDAO();

    public Optional<User> login(String username, String rawPassword) throws SQLException {
        if (username == null || rawPassword == null) {
            return Optional.empty();
        }
        Optional<AuthRow> row = userDAO.findByUsername(username.trim());
        if (row.isEmpty()) {
            return Optional.empty();
        }
        String expected = row.get().passwordHash();
        String actual = sha256(rawPassword);
        if (!expected.equalsIgnoreCase(actual)) {
            return Optional.empty();
        }
        return Optional.of(row.get().user());
    }

    public List<User> listUsers() throws SQLException {
        return userDAO.getAll();
    }

    public void createUser(String username, String rawPassword, Role role) throws SQLException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        userDAO.create(username.trim(), sha256(rawPassword), role);
    }

    public void deleteUser(int userId) throws SQLException {
        userDAO.delete(userId);
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
