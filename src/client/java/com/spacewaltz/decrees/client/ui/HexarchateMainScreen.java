package com.spacewaltz.decrees.client.ui;

import com.spacewaltz.decrees.client.economy.ClientEconomyState;
import com.spacewaltz.decrees.client.guild.ClientGuildState;
import com.spacewaltz.decrees.economy.EconomyConfig;
import com.spacewaltz.decrees.economy.EconomyConfigData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * G-key panel:
 * - Header with player name and compact G/S/C balance.
 * - Single "Ledger" tab showing recent transactions.
 */
public class HexarchateMainScreen extends Screen {

    private enum Tab {
        LEDGER,
        GUILD
    }

    private Tab activeTab = Tab.LEDGER;

    // Buttons we need to toggle visibility for
    private ButtonWidget ledgerTabButton;
    private ButtonWidget guildTabButton;
    private ButtonWidget depositButton;
    private ButtonWidget leaveButton;
    private ButtonWidget invitesButton;


    public HexarchateMainScreen() {
        super(Text.literal("Hexarchate Panel"));
    }

    @Override
    protected void init() {
        super.init();

        // Ask server for fresh snapshots whenever the screen opens.
        ClientEconomyState.requestSnapshot();
        ClientGuildState.requestSnapshot();

        this.clearChildren();

        int centerX = this.width / 2;
        int navY = 44;
        int buttonWidth = 80;
        int buttonHeight = 20;

        // Top nav buttons
        ledgerTabButton = ButtonWidget.builder(Text.literal("Ledger"), button -> {
                    activeTab = Tab.LEDGER;
                })
                .dimensions(centerX - buttonWidth - 4, navY, buttonWidth, buttonHeight)
                .build();

        guildTabButton = ButtonWidget.builder(Text.literal("Guild"), button -> {
                    activeTab = Tab.GUILD;
                })
                .dimensions(centerX + 4, navY, buttonWidth, buttonHeight)
                .build();

        this.addDrawableChild(ledgerTabButton);
        this.addDrawableChild(guildTabButton);

        // Bottom action buttons (used on Guild tab)
        int actionY = this.height - 28;
        int actionWidth = 90;

        depositButton = ButtonWidget.builder(Text.literal("Deposit"), button ->
                        openChatWithCommand("/guild deposit "))
                .dimensions(16, actionY, actionWidth, buttonHeight)
                .build();

        leaveButton = ButtonWidget.builder(Text.literal("Leave Guild"), button -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null && client.player != null && client.player.networkHandler != null) {
                        client.player.networkHandler.sendCommand("guild leave");
                    }
                })
                .dimensions(16 + actionWidth + 6, actionY, actionWidth, buttonHeight)
                .build();

        invitesButton = ButtonWidget.builder(Text.literal("Invites"), button ->
                        openChatWithCommand("/guild accept"))
                .dimensions(this.width - actionWidth - 16, actionY, actionWidth, buttonHeight)
                .build();

        this.addDrawableChild(depositButton);
        this.addDrawableChild(leaveButton);
        this.addDrawableChild(invitesButton);

        updateGuildButtonsVisibility();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Semi-transparent dark overlay on top of the world (no blur).
        // 0x60 = ~38% opacity so you still see the world, but text sits on a darker field.
        context.fill(0, 0, this.width, this.height, 0x60000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Call our custom background (tinted world, not black / not blurred).
        this.renderBackground(context, mouseX, mouseY, delta);

        // Title
        String title = "Hexarchate Panel";
        int titleWidth = this.textRenderer.getWidth(title);
        context.drawText(
                this.textRenderer,
                title,
                (this.width - titleWidth) / 2,
                10,
                0xFFE091,
                true
        );

        // Header row: player + balance + guild summary
        renderHeader(context);

        // Active tab content
        if (activeTab == Tab.LEDGER) {
            renderLedgerTab(context);
        } else if (activeTab == Tab.GUILD) {
            renderGuildTab(context);
        }

        // Toggle guild action buttons
        updateGuildButtonsVisibility();

        // Draw buttons and other children
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderHeader(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = (client != null && client.player != null)
                ? client.player.getName().getString()
                : "Unknown";

        String playerText = "Player: " + playerName;
        int leftX = 16;
        int y = 26;

        context.drawText(
                this.textRenderer,
                playerText,
                leftX,
                y,
                0xFFFFFFFF,
                true
        );

        long balanceCopper = ClientEconomyState.getBalanceCopper();
        String balanceGsc = formatBalanceGSC(balanceCopper);

        String balanceText = "Balance: " + balanceGsc;
        int balanceWidth = this.textRenderer.getWidth(balanceText);
        int rightX = this.width - 16 - balanceWidth;

        context.drawText(
                this.textRenderer,
                balanceText,
                rightX,
                y,
                0xFFFFFFFF,
                true
        );

        // Guild summary line
        String guildLine;
        if (ClientGuildState.isInGuild()) {
            String guildName = ClientGuildState.getGuildName();
            String myTitle = ClientGuildState.getMyTitle();
            guildLine = "Guild: " + guildName + " (" + myTitle + ")";
        } else {
            int invites = ClientGuildState.getPendingInvites();
            if (invites > 0) {
                guildLine = "Guild: None (invites: " + invites + ")";
            } else {
                guildLine = "Guild: None";
            }
        }

        context.drawText(
                this.textRenderer,
                guildLine,
                leftX,
                y + 10,
                0xFFCCCCCC,
                true
        );
    }

    private void renderLedgerTab(DrawContext context) {
        List<String> lines = ClientEconomyState.getRecentLines();

        int panelLeft = 16;
        int panelTop = 70;
        int panelRight = this.width - 16;
        int panelBottom = this.height - 24;

        // Very dark panel behind the ledger text (almost opaque).
        context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xF0101010);

        String header = "Recent Transactions";
        context.drawText(
                this.textRenderer,
                header,
                panelLeft + 8,
                panelTop + 6,
                0xFFE091,
                true   // shadow
        );

        int x = panelLeft + 8;
        int yStart = panelTop + 20;
        int lineHeight = 10;
        int maxLines = ((panelBottom - yStart) / lineHeight) - 1;
        if (maxLines < 1) {
            maxLines = 1;
        }

        if (lines.isEmpty()) {
            context.drawText(
                    this.textRenderer,
                    "No recent transactions.",
                    x,
                    yStart,
                    0xFFFFFFFF,
                    true
            );
            return;
        }

        int y = yStart;
        int count = 0;
        for (String line : lines) {
            if (count >= maxLines) break;

            context.drawText(
                    this.textRenderer,
                    line,
                    x,
                    y,
                    0xFFFFFFFF,
                    true   // shadow
            );

            y += lineHeight;
            count++;
        }
    }

    private void renderGuildTab(DrawContext context) {
        int panelLeft = 16;
        int panelTop = 70;
        int panelRight = this.width - 16;
        int panelBottom = this.height - 50; // leave space for buttons

        // Dark panel background
        context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xF0101010);

        int x = panelLeft + 8;
        int y = panelTop + 8;

        if (!ClientGuildState.isInGuild()) {
            String line1 = "You are not currently in a guild.";
            context.drawText(this.textRenderer, line1, x, y, 0xFFFFFFFF, true);
            y += 10;

            int invites = ClientGuildState.getPendingInvites();
            if (invites > 0) {
                String line2 = "Use /guild accept to join (" + invites + " invite"
                        + (invites > 1 ? "s" : "") + " pending).";
                context.drawText(this.textRenderer, line2, x, y, 0xFFFFFFFF, true);
            } else {
                String line2 = "Ask a guild leader/officer to invite you.";
                context.drawText(this.textRenderer, line2, x, y, 0xFFFFFFFF, true);
            }
            return;
        }

        String guildName = ClientGuildState.getGuildName();
        String myTitle = ClientGuildState.getMyTitle();
        String leaderName = ClientGuildState.getLeaderName();
        int totalMembers = ClientGuildState.getTotalMembers();
        int officers = ClientGuildState.getOfficerCount();
        int veterans = ClientGuildState.getVeteranCount();
        int members = ClientGuildState.getMemberCount();
        int recruits = ClientGuildState.getRecruitCount();
        long treasuryCopper = ClientGuildState.getTreasuryCopper();
        String treasuryText = formatBalanceGSC(treasuryCopper);

        context.drawText(this.textRenderer,
                "Guild: " + guildName,
                x, y, 0xFFE091, true);
        y += 10;

        context.drawText(this.textRenderer,
                "Your rank: " + myTitle,
                x, y, 0xFFFFFFFF, true);
        y += 10;

        context.drawText(this.textRenderer,
                "Leader: " + (leaderName == null || leaderName.isBlank() ? "Unknown" : leaderName),
                x, y, 0xFFFFFFFF, true);
        y += 10;

        String membersLine = "Members: " + totalMembers
                + "  (" + officers + " Officer(s), "
                + veterans + " Veteran(s), "
                + members + " Member(s), "
                + recruits + " Recruit(s))";
        context.drawText(this.textRenderer, membersLine, x, y, 0xFFFFFFFF, true);
        y += 10;

        context.drawText(this.textRenderer,
                "Treasury: " + treasuryText,
                x, y, 0xFFFFFFFF, true);
        y += 10;

        int invites = ClientGuildState.getPendingInvites();
        if (invites > 0) {
            context.drawText(this.textRenderer,
                    "Invites pending: " + invites,
                    x, y, 0xFFCCCCCC, true);
        }
    }

    private void updateGuildButtonsVisibility() {
        boolean onGuildTab = (activeTab == Tab.GUILD);

        if (depositButton != null) {
            depositButton.visible = onGuildTab;
            depositButton.active = onGuildTab && ClientGuildState.isInGuild();
        }

        if (leaveButton != null) {
            leaveButton.visible = onGuildTab;
            leaveButton.active = onGuildTab && ClientGuildState.isInGuild();
        }

        if (invitesButton != null) {
            int invites = ClientGuildState.getPendingInvites();
            invitesButton.visible = onGuildTab;
            invitesButton.active = onGuildTab && invites > 0;

            String base = "Invites";
            if (invites > 0) {
                invitesButton.setMessage(Text.literal(base + " (" + invites + ")"));
            } else {
                invitesButton.setMessage(Text.literal(base));
            }
        }
    }

    private void openChatWithCommand(String commandPrefix) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.setScreen(new ChatScreen(commandPrefix));
    }


    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------
    // G/S/C formatting helper
    // ------------------------------------------------------------

    private String formatBalanceGSC(long totalCopper) {
        EconomyConfigData cfg = EconomyConfig.get();
        int cps = Math.max(1, cfg.copperPerSilver);
        int spg = Math.max(1, cfg.silverPerGold);

        long copperPerGold = (long) cps * (long) spg;

        long gold = totalCopper / copperPerGold;
        long remainder = totalCopper % copperPerGold;

        long silver = remainder / cps;
        long copper = remainder % cps;

        return gold + "G " + silver + "S " + copper + "C";
    }
}
