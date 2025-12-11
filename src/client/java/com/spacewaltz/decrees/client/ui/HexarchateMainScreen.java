package com.spacewaltz.decrees.client.ui;

import com.spacewaltz.decrees.client.economy.ClientEconomyState;
import com.spacewaltz.decrees.economy.EconomyConfig;
import com.spacewaltz.decrees.economy.EconomyConfigData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
        LEDGER
    }

    private Tab activeTab = Tab.LEDGER;

    public HexarchateMainScreen() {
        super(Text.literal("Hexarchate Panel"));
    }

    @Override
    protected void init() {
        super.init();

        // Ask server for fresh snapshot whenever the screen opens.
        ClientEconomyState.requestSnapshot();

        this.clearChildren();

        int centerX = this.width / 2;
        int navY = 44;
        int buttonWidth = 80;
        int buttonHeight = 20;

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Ledger"), button -> {
                            activeTab = Tab.LEDGER;
                        })
                        .dimensions(centerX - (buttonWidth / 2), navY, buttonWidth, buttonHeight)
                        .build()
        );
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
                true   // draw shadow for readability
        );

        // Header row: player + balance
        renderHeader(context);

        // Active tab content
        if (activeTab == Tab.LEDGER) {
            renderLedgerTab(context);
        }

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
                true   // shadow
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
                true   // shadow
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
