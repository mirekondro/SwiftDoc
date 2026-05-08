package dk.easv.swiftdoc.model;

/**
 * Represents a client that owns multiple scanning profiles.
 */
public class Client {

    private int clientId;
    private String clientName;

    public Client(int clientId, String clientName) {
        this.clientId = clientId;
        this.clientName = clientName;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    @Override
    public String toString() {
        return clientName;
    }
}

