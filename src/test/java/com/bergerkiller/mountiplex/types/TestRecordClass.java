package com.bergerkiller.mountiplex.types;

/**
 * Mocks the behavior of a jdk14+ record class.
 */
public final class TestRecordClass {
    private final String field0_text;
    private final long field1_long;
    private final int field2_x;
    private final int field3_y;
    private final int field4_z;

    public TestRecordClass(String field0_text, long field1_long, int field2_x, int field3_y, int field4_z) {
        this.field0_text = field0_text;
        this.field1_long = field1_long;
        this.field2_x = field2_x;
        this.field3_y = field3_y;
        this.field4_z = field4_z;
    }

    public String field0_text() {
        return field0_text;
    }

    public long field1_long() { return field1_long; }

    public int field2_x() {
        return field2_x;
    }

    public int field3_y() {
        return field3_y;
    }

    public int field4_z() {
        return field4_z;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TestRecordClass) {
            TestRecordClass other = (TestRecordClass) o;
            return this.field0_text.equals(other.field0_text) &&
                    this.field1_long == other.field1_long &&
                    this.field2_x == other.field2_x &&
                    this.field3_y == other.field3_y &&
                    this.field4_z == other.field4_z;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{field0_text=" + field0_text +
                ", field1_long=" + field1_long +
                ", field2_x=" + field2_x +
                ", field3_y=" + field3_y +
                ", field4_z=" + field4_z + "}";
    }
}
