package com.bergerkiller.mountiplex.conversion2.builtin;

import com.bergerkiller.mountiplex.conversion2.Conversion;
import com.bergerkiller.mountiplex.conversion2.Converter;

public class NumberConversion {
    public static void register() {
        // Parse String to Byte
        Conversion.registerConverter(new IntegerNumberParser<Byte>(Byte.class) {
            @Override
            public Byte parse(String value) {
                return Byte.parseByte(value);
            }
        });

        // Parse String to Short
        Conversion.registerConverter(new IntegerNumberParser<Short>(Short.class) {
            @Override
            public Short parse(String value) {
                return Short.parseShort(value);
            }
        });

        // Parse String to Integer
        Conversion.registerConverter(new IntegerNumberParser<Integer>(Integer.class) {
            @Override
            public Integer parse(String value) {
                return Integer.parseInt(value);
            }
        });

        // Parse String to Long
        Conversion.registerConverter(new IntegerNumberParser<Long>(Long.class) {
            @Override
            public Long parse(String value) {
                return Long.parseLong(value);
            }
        });

        // Parse String to Float
        Conversion.registerConverter(new FloatingNumberParser<Float>(Float.class) {
            @Override
            public Float parse(String value) {
                return Float.parseFloat(value);
            }
        });

        // Parse String to Double
        Conversion.registerConverter(new FloatingNumberParser<Double>(Double.class) {
            @Override
            public Double parse(String value) {
                return Double.parseDouble(value);
            }
        });

        // Parse String to Number, selecting the smallest possible number type that can represent the number
        Conversion.registerConverter(new NumberParser<Number>(Number.class) {
            @Override
            public Number parse(String value, boolean isFloatingPoint) throws NumberFormatException {
                if (isFloatingPoint) {
                    double result = Double.parseDouble(value);
                    float resultF = (float) result;
                    if (result == (double) resultF) {
                        return resultF;
                    } else {
                        return result;
                    }
                } else {
                    long result = Long.parseLong(value);
                    if (result >= Byte.MIN_VALUE && result <= Byte.MAX_VALUE) {
                        return (byte) result;
                    } else if (result >= Short.MIN_VALUE && result <= Short.MAX_VALUE) {
                        return (short) result;
                    } else if (result >= Integer.MIN_VALUE && result < Integer.MAX_VALUE) {
                        return (int) result;
                    } else {
                        return result;
                    }
                }
            }
        });

        // Cast Number to Byte
        Conversion.registerConverter(new NumberCaster<Byte>(Byte.class) {
            @Override
            public Byte convertInput(Number value) {
                return value.byteValue();
            }
        });

        // Cast Number to Short
        Conversion.registerConverter(new NumberCaster<Short>(Short.class) {
            @Override
            public Short convertInput(Number value) {
                return value.shortValue();
            }
        });

        // Cast Number to Integer
        Conversion.registerConverter(new NumberCaster<Integer>(Integer.class) {
            @Override
            public Integer convertInput(Number value) {
                return value.intValue();
            }
        });

        // Cast Number to Long
        Conversion.registerConverter(new NumberCaster<Long>(Long.class) {
            @Override
            public Long convertInput(Number value) {
                return value.longValue();
            }
        });

        // Cast Number to Float
        Conversion.registerConverter(new NumberCaster<Float>(Float.class) {
            @Override
            public Float convertInput(Number value) {
                return value.floatValue();
            }
        });

        // Cast Number to Double
        Conversion.registerConverter(new NumberCaster<Double>(Double.class) {
            @Override
            public Double convertInput(Number value) {
                return value.doubleValue();
            }
        });
    }

    private static abstract class NumberParser <T> extends Converter<String, T> {

        public NumberParser(Class<T> output) {
            super(String.class, output);
        }

        public abstract T parse(String value, boolean isFloatingPoint) throws NumberFormatException;

        @Override
        public T convertInput(String value) {
            try {
                // Filter non-numeric contents from the String, and then parse it
                StringBuilder rval = new StringBuilder(value.length());
                boolean hasComma = false;
                boolean hasDigit = false;
                for (int i = 0; i < value.length(); i++) {
                    char c = value.charAt(i);
                    if (Character.isDigit(c)) {
                        rval.append(c);
                        hasDigit = true;
                    } else if (c == ' ') {
                        if (hasDigit) {
                            break;
                        }
                    } else if ((c == ',' || c == '.') && !hasComma) {
                        rval.append('.');
                        hasComma = true;
                    } else if (c == '-' && rval.length() == 0) {
                        rval.append(c);
                    }
                }
                if (hasDigit) {
                    return parse(rval.toString(), hasComma);
                }
            } catch (NumberFormatException ex) {}
            return null;
        }
    }

    private static abstract class FloatingNumberParser <T> extends NumberParser<T> {

        public FloatingNumberParser(Class<T> output) {
            super(output);
        }

        @Override
        public final T parse(String value, boolean isFloatingPoint) throws NumberFormatException {
            if (isFloatingPoint) {
                return parse(value);
            } else {
                return parse(value + ".0");
            }
        }

        public abstract T parse(String value) throws NumberFormatException;
    }

    private static abstract class IntegerNumberParser <T> extends NumberParser<T> {

        public IntegerNumberParser(Class<T> output) {
            super(output);
        }

        @Override
        public final T parse(String value, boolean isFloatingPoint) throws NumberFormatException {
            if (isFloatingPoint) {
                return parse(value.substring(0, value.indexOf('.')));
            } else {
                return parse(value);
            }
        }

        public abstract T parse(String value) throws NumberFormatException;
    }

    private static abstract class NumberCaster <T> extends Converter<Number, T> {
        public NumberCaster(Class<T> output) {
            super(Number.class, output);
        }
    }
}
