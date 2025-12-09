package com.spacewaltz.decrees.decree;

public enum DecreeStatus {
    DRAFT,
    VOTING,
    ENACTED,
    REJECTED,
    CANCELLED   // <-- new final state for withdrawn / void decrees
}
