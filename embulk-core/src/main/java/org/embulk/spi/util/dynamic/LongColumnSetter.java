package org.embulk.spi.util.dynamic;

import java.time.Instant;
import org.embulk.spi.Column;
import org.embulk.spi.PageBuilder;
import org.msgpack.value.Value;

public class LongColumnSetter extends AbstractDynamicColumnSetter {
    public LongColumnSetter(PageBuilder pageBuilder, Column column,
            DefaultValueSetter defaultValue) {
        super(pageBuilder, column, defaultValue);
    }

    @Override
    public void setNull() {
        pageBuilder.setNull(column);
    }

    @Override
    public void set(boolean v) {
        pageBuilder.setLong(column, v ? 1L : 0L);
    }

    @Override
    public void set(long v) {
        pageBuilder.setLong(column, v);
    }

    @Override
    public void set(double v) {
        long lv;
        try {
            final double roundedDouble = Math.rint(v);
            final double diff = v - roundedDouble;
            if (Math.abs(diff) == 0.5) {
                lv = (long) (v + Math.copySign(0.5, v));
            } else {
                lv = (long) roundedDouble;
            }
        } catch (ArithmeticException ex) {
            // NaN / Infinite / -Infinite
            defaultValue.setLong(pageBuilder, column);
            return;
        }
        pageBuilder.setLong(column, lv);
    }

    @Override
    public void set(String v) {
        long lv;
        try {
            lv = Long.parseLong(v);
        } catch (NumberFormatException e) {
            defaultValue.setLong(pageBuilder, column);
            return;
        }
        pageBuilder.setLong(column, lv);
    }

    @Override
    @SuppressWarnings("deprecation")  // https://github.com/embulk/embulk/issues/1292
    public void set(final org.embulk.spi.time.Timestamp v) {
        pageBuilder.setLong(column, v.getEpochSecond());
    }

    @Override
    public void set(Instant v) {
        pageBuilder.setLong(column, v.getEpochSecond());
    }

    @Override
    public void set(Value v) {
        defaultValue.setLong(pageBuilder, column);
    }
}
