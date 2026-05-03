package de.klausiiiii.mobArmyBattle.pool;

import java.util.Objects;

public final class MobEntry {

    private final String entityTypeName;
    private final String equipmentSignature;

    public MobEntry(String entityTypeName, String equipmentSignature) {
        if (entityTypeName == null || entityTypeName.isBlank()) {
            throw new IllegalArgumentException("entityTypeName darf nicht leer sein");
        }
        if (equipmentSignature == null) {
            throw new IllegalArgumentException("equipmentSignature darf nicht null sein");
        }
        this.entityTypeName = entityTypeName;
        this.equipmentSignature = equipmentSignature;
    }

    public String getEntityTypeName() {
        return entityTypeName;
    }

    public String getEquipmentSignature() {
        return equipmentSignature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MobEntry that)) return false;
        return entityTypeName.equals(that.entityTypeName)
                && equipmentSignature.equals(that.equipmentSignature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityTypeName, equipmentSignature);
    }

    @Override
    public String toString() {
        return "MobEntry{" + entityTypeName + ", eq=" + equipmentSignature + "}";
    }
}
