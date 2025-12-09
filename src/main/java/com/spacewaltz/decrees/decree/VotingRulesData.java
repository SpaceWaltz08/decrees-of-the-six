package com.spacewaltz.decrees.decree;

public class VotingRulesData {
    public String majorityMode = "SIMPLE"; // SIMPLE or TWO_THIRDS
    public int minQuorumPercent = 50;      // 0..100
    public boolean tiesPass = false;       // if true, tie = ENACTED
    public int votingDurationMinutes = 0;  // 0 = no time limit
}
