package com.captainalm.mesh;

public enum FragmentIndicator {
    Unknown(0),
    NodeNavigator(1),
    AllowedNodes(2),
    BlockedNodes(3),
    AllowedNodeSignatureKeys(4),
    PeeringRequests(5);
    private final int id;
    FragmentIndicator(int id) {
        this.id = id;
    }
    public int getID() {
        return id;
    }
    public static FragmentIndicator getIndicator(int id) {
        switch (id) {
            case 1 -> {
                return NodeNavigator;
            }
            case 2 -> {
                return AllowedNodes;
            }
            case 3 -> {
                return BlockedNodes;
            }
            case 4 -> {
                return AllowedNodeSignatureKeys;
            }
            case 5 -> {
                return PeeringRequests;
            }
            default -> {
                return Unknown;
            }
        }
    }
}
