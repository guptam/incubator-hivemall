/*
 * Hivemall: Hive scalable Machine Learning Library
 *
 * Copyright (C) 2015 Makoto YUI
 * Copyright (C) 2013-2015 National Institute of Advanced Industrial Science and Technology (AIST)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hivemall.fm;

import java.nio.ByteBuffer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.serde2.objectinspector.ListObjectInspector;

public abstract class Feature {

    protected double value;

    public Feature() {}

    public Feature(double value) {
        this.value = value;
    }

    public void setFeature(@Nonnull String f) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    public String getFeature() {
        throw new UnsupportedOperationException();
    }

    public void setFeatureIndex(int i) {
        throw new UnsupportedOperationException();
    }

    public int getFeatureIndex() {
        throw new UnsupportedOperationException();
    }

    public void setField(@Nullable String f) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    public String getField() {
        throw new UnsupportedOperationException();
    }

    public double getValue() {
        return value;
    }

    public abstract int bytes();

    public abstract void writeTo(@Nonnull ByteBuffer dst);

    public abstract void readFrom(@Nonnull ByteBuffer src);

    public static int requiredBytes(@Nonnull final Feature[] x) {
        int ret = 0;
        for (Feature f : x) {
            assert (f != null);
            ret += f.bytes();
        }
        return ret;
    }

    @Nullable
    public static Feature[] parseFeatures(@Nonnull final Object arg,
            @Nonnull final ListObjectInspector listOI, @Nullable final Feature[] probes,
            final boolean asIntFeature) throws HiveException {
        if (arg == null) {
            return null;
        }

        final int length = listOI.getListLength(arg);
        final Feature[] ary;
        if (probes != null && probes.length == length) {
            ary = probes;
        } else {
            ary = new Feature[length];
        }

        int j = 0;
        for (int i = 0; i < length; i++) {
            Object o = listOI.getListElement(arg, i);
            if (o == null) {
                continue;
            }
            String s = o.toString();
            Feature f = ary[j];
            if (f == null) {
                f = parse(s, asIntFeature);
            } else {
                parse(s, f, asIntFeature);
            }
            ary[j] = f;
            j++;
        }
        if (j == length) {
            return ary;
        } else {
            Feature[] dst = new Feature[j];
            System.arraycopy(ary, 0, dst, 0, j);
            return dst;
        }
    }

    @Nonnull
    static Feature parse(@Nonnull final String fv, final boolean asIntFeature) throws HiveException {
        final int pos1 = fv.indexOf(':');
        if (pos1 == -1) {
            if (asIntFeature) {
                int index = parseFeatureIndex(fv);
                return new IntFeature(index, 1.d);
            } else {
                return new StringFeature(/* index */fv, 1.d);
            }
        } else {
            String lead = fv.substring(0, pos1);
            String rest = fv.substring(pos1 + 1);
            int pos2 = rest.indexOf(':');
            if (pos2 == -1) {
                if (asIntFeature) {
                    int index = parseFeatureIndex(lead);
                    double value = parseFeatureValue(rest);
                    return new IntFeature(index, value);
                } else {
                    double value = parseFeatureValue(rest);
                    return new StringFeature(/* index */lead, value);
                }
            } else {
                if (asIntFeature) {
                    throw new HiveException("Fields are currently unsupported with IntFeatures: "
                            + fv);
                }
                String index = rest.substring(0, pos2);
                String valueStr = rest.substring(pos2 + 1);
                double value = parseFeatureValue(valueStr);
                return new StringFeature(index, /* field */lead, value);
            }
        }
    }

    static void parse(@Nonnull final String fv, @Nonnull final Feature probe,
            final boolean asIntFeature) throws HiveException {
        final int pos1 = fv.indexOf(":");
        if (pos1 == -1) {
            if (asIntFeature) {
                int index = parseFeatureIndex(fv);
                probe.setFeatureIndex(index);
            } else {
                probe.setField(null);
                probe.setFeature(fv);
            }
            probe.value = 1.d;
        } else {
            String lead = fv.substring(0, pos1);
            String rest = fv.substring(pos1 + 1);
            int pos2 = rest.indexOf(':');
            if (pos2 == -1) {
                if (asIntFeature) {
                    int index = parseFeatureIndex(lead);
                    probe.setFeatureIndex(index);
                    probe.value = parseFeatureValue(rest);;
                } else {
                    probe.setField(null);
                    probe.setFeature(lead);
                    probe.value = parseFeatureValue(rest);
                }
            } else {
                if (asIntFeature) {
                    throw new HiveException("Fields are currently unsupported with IntFeatures: "
                            + fv);
                }
                String index = rest.substring(0, pos2);
                String valueStr = rest.substring(pos2 + 1);
                probe.setField(lead);
                probe.setFeature(index);
                probe.value = parseFeatureValue(valueStr);
            }
        }
    }

    private static int parseFeatureIndex(@Nonnull final String indexStr) throws HiveException {
        final int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            throw new HiveException("Invalid index value: " + indexStr, e);
        }
        if (index < 0) {
            throw new HiveException("Feature index MUST be greater than 0: " + indexStr);
        }
        return index;
    }

    private static double parseFeatureValue(@Nonnull final String value) throws HiveException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new HiveException("Invalid feature value: " + value, e);
        }
    }

    @Nonnull
    public static Feature createInstance(@Nonnull ByteBuffer src, boolean asIntFeature) {
        if (asIntFeature) {
            return new IntFeature(src);
        } else {
            return new StringFeature(src);
        }
    }
}
