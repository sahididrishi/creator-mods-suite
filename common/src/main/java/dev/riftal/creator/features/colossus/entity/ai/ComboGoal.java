package dev.riftal.creator.features.colossus.entity.ai;

import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import dev.riftal.creator.features.colossus.net.ScreenShakePayload;
import net.minecraft.server.level.ServerLevel;

/**
 * Phase 3 only: three swings in two and a half seconds, the last one the heaviest. The boss keeps
 * tracking its target between swings, so a player who backs off eats the tail of the combo.
 */
public class ComboGoal extends AnimatedAttackGoal {

    public ComboGoal(AshenColossusEntity boss) {
        super(boss, AttackKind.COMBO);
    }

    @Override
    protected void onHit(ServerLevel level, int index) {
        this.boss.comboHit(level, index);
        if (index == AttackKind.COMBO.hitCount() - 1) {
            ScreenShakePayload.sendAround(level, this.boss.position(), 0.3F, 8, 16.0F);
        }
    }

    @Override
    protected void onTick(ServerLevel level, int tick) {
        if (tick < AttackKind.COMBO.hitTick(AttackKind.COMBO.hitCount() - 1)) {
            this.faceTarget();
        }
    }
}
