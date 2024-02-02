package frc.robot.Drivers.Visions;

import frc.robot.Utils.MathUtils.Rotation2D;
import frc.robot.Utils.MathUtils.Vector2D;

import java.util.List;

public interface TargetFieldPositionTracker {
    List<TargetOnField> getAllTargets();
    List<TargetOnField> getVisibleTargets();
    void update(Vector2D robotPositionInField2D, Rotation2D robotRotation);
    /**
     * whether the target is visible
     * @param id the id of the target
     * @return true for visible, false for not seen
     * */
    default boolean isTargetVisible(int id) {
        return getTargetByID(id) != null;
    }

    /**
     * gets the target of selected id
     * @param id the id of the target
     * @return the target instance, null for unseen
     * */
    default TargetOnField getTargetByID(int id) {// find specific apriltag
        for (TargetOnField target:getVisibleTargets())
            if (target.id == id)
                return target;
        return null;
    }

    final class TargetOnField {
        public final int id;
        public final Vector2D fieldPosition;
        private final long timeLastSeenMillis;
        public long timeMillisSinceLastContact() {
            return (System.currentTimeMillis() - timeLastSeenMillis);
        }
        public TargetOnField(int id, Vector2D fieldPosition) {
            this.id = id;
            this.fieldPosition = fieldPosition;
            this.timeLastSeenMillis = System.currentTimeMillis();
        }
    }
}
