/*
 * Copyright 2016 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.geofence;
import java.text.ParseException;

public class GeofencePoint extends GeofenceGeometry {

    private double latitude;
    private double longitude;

    public GeofencePoint() {
    }

    public GeofencePoint(String wkt) throws ParseException {
        fromWkt(wkt);
    }

    public GeofencePoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public boolean containsPoint(double latitude, double longitude) {
        return this.latitude == latitude && this.longitude == longitude;
    }

    @Override
    public String toWkt() {
        String wkt = "";
        wkt = "POINT (";
        wkt += String.valueOf(latitude);
        wkt += ",";
        wkt += String.valueOf(longitude);
        wkt += ")";
        return wkt;
    }

    @Override
    public void fromWkt(String wkt) throws ParseException {
        if (!wkt.startsWith("POINT")) {
            throw new ParseException("Mismatch geometry type", 0);
        }
        String content = wkt.substring(wkt.indexOf("(") + 1, wkt.indexOf(")"));
        if (content == null || content.equals("")) {
            throw new ParseException("No content", 0);
        }
        String[] commaTokens = content.split(",");
        if (commaTokens.length != 2) {
            throw new ParseException("Not valid content", 0);
        }
        try {
            latitude = Double.parseDouble(commaTokens[0]);
        } catch (NumberFormatException e) {
            throw new ParseException(commaTokens[0] + " is not a double", 0);
        }
        try {
            longitude = Double.parseDouble(commaTokens[1]);
        } catch (NumberFormatException e) {
            throw new ParseException(commaTokens[1] + " is not a double", 0);
        }
    }
}
