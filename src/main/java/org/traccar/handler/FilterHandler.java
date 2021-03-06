/*
 * Copyright 2014 - 2018 Anton Tananaev (anton@traccar.org)
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
package org.traccar.handler;

import io.netty.channel.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.BaseDataHandler;
import org.traccar.Context;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@ChannelHandler.Sharable
public class FilterHandler extends BaseDataHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterHandler.class);

    private boolean filterInvalid;
    private boolean filterZero;
    private boolean filterDuplicate;
    private long filterFuture;
    private boolean filterApproximate;
    private int filterAccuracy;
    private boolean filterStatic;
    private int filterDistance;
    private int filterMaxSpeed;
    private long filterMinPeriod;
    private long skipLimit;
    private boolean skipAttributes;

    public FilterHandler(Config config) {
        filterInvalid = config.getBoolean(Keys.FILTER_INVALID);
        filterZero = config.getBoolean(Keys.FILTER_ZERO);
        filterDuplicate = config.getBoolean(Keys.FILTER_DUPLICATE);
        filterFuture = config.getLong(Keys.FILTER_FUTURE) * 1000;
        filterAccuracy = config.getInteger(Keys.FILTER_ACCURACY);
        filterApproximate = config.getBoolean(Keys.FILTER_APPROXIMATE);
        filterStatic = config.getBoolean(Keys.FILTER_STATIC);
        filterDistance = config.getInteger(Keys.FILTER_DISTANCE);
        filterMaxSpeed = config.getInteger(Keys.FILTER_MAX_SPEED);
        filterMinPeriod = config.getInteger(Keys.FILTER_MIN_PERIOD) * 1000;
        skipLimit = config.getLong(Keys.FILTER_SKIP_LIMIT) * 1000;
        skipAttributes = config.getBoolean(Keys.FILTER_SKIP_ATTRIBUTES_ENABLE);
    }

    private boolean filterInvalid(Position position) {
        return filterInvalid && (!position.getValid()
           || position.getLatitude() > 90 || position.getLongitude() > 180
           || position.getLatitude() < -90 || position.getLongitude() < -180);
    }

    private boolean filterZero(Position position) {
        return filterZero && position.getLatitude() == 0.0 && position.getLongitude() == 0.0;
    }

    private boolean filterDuplicate(Position position, Position last) {
        if (filterDuplicate && last != null && position.getFixTime().equals(last.getFixTime())) {
            for (String key : position.getAttributes().keySet()) {
                if (!last.getAttributes().containsKey(key)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean filterFuture(Position position) {
        return filterFuture != 0 && position.getFixTime().getTime() > System.currentTimeMillis() + filterFuture;
    }

    private boolean filterAccuracy(Position position) {
        return filterAccuracy != 0 && position.getAccuracy() > filterAccuracy;
    }

    private boolean filterApproximate(Position position) {
        return filterApproximate && position.getBoolean(Position.KEY_APPROXIMATE);
    }

    private boolean filterStatic(Position position) {
        return filterStatic && position.getSpeed() == 0.0;
    }

    private boolean filterDistance(Position position, Position last) {
        if (filterDistance != 0 && last != null) {
            return position.getDouble(Position.KEY_DISTANCE) < filterDistance;
        }
        return false;
    }

    private boolean filterMaxSpeed(Position position, Position last) {
        if (filterMaxSpeed != 0 && last != null) {
            double distance = position.getDouble(Position.KEY_DISTANCE);
            double time = position.getFixTime().getTime() - last.getFixTime().getTime();
            return UnitsConverter.knotsFromMps(distance / (time / 1000)) > filterMaxSpeed;
        }
        return false;
    }

    private boolean filterMinPeriod(Position position, Position last) {
        if (filterMinPeriod != 0 && last != null) {
            long time = position.getFixTime().getTime() - last.getFixTime().getTime();
            return time > 0 && time < filterMinPeriod;
        }
        return false;
    }

    private boolean skipLimit(Position position, Position last) {
        if (skipLimit != 0 && last != null) {
            return (position.getServerTime().getTime() - last.getServerTime().getTime()) > skipLimit;
        }
        return false;
    }

    private boolean skipAttributes(Position position) {
        if (skipAttributes) {
            String attributesString = Context.getIdentityManager().lookupAttributeString(
                    position.getDeviceId(), "filter.skipAttributes", "", false, true);
            for (String attribute : attributesString.split("[ ,]")) {
                if (position.getAttributes().containsKey(attribute)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean filter(Position position) {

        StringBuilder filterType = new StringBuilder();

        Position last = null;
        if (Context.getIdentityManager() != null) {
            last = Context.getIdentityManager().getLastPosition(position.getDeviceId());
        }

        if (filterInvalid(position)) {
            filterType.append("Invalid (" + position.getLatitude() + "," + position.getLongitude() + ") ");
        }
        if (filterZero(position)) {
            filterType.append("Zero (" + position.getLatitude() + "," + position.getLongitude() + ") ");
        }
        if (filterDuplicate(position, last) && !skipLimit(position, last) && !skipAttributes(position)) {
            if (last != null) {
                filterType.append("Duplicate-Curr (("
                        + position.getServerTime() + "||" + position.getFixTime() + "),"
                        + position.getLatitude() + "," + position.getLongitude() + ","
                        + position.getSpeed() + ")");
                filterType.append("Duplicate-Last (("
                        + last.getServerTime() + "||" + last.getFixTime() + "),"
                        + last.getLatitude() + "," + last.getLongitude() + ","
                        + last.getSpeed() + ")");
            } else {
                filterType.append("Duplicate ("
                        + position.getLatitude() + "," + position.getLongitude() + ","
                        + position.getSpeed() + ") ");
            }
        }
        if (filterFuture(position)) {
            filterType.append("Future (" + position.getLatitude() + "," + position.getLongitude() + ") ");
        }
        if (filterAccuracy(position)) {
            filterType.append("Accuracy (" + position.getLatitude() + "," + position.getLongitude() + ") ");
        }
        if (filterApproximate(position)) {
            filterType.append("Approximate (" + position.getLatitude() + "," + position.getLongitude() + ") ");
        }
        if (filterStatic(position) && !skipLimit(position, last) && !skipAttributes(position)) {
            filterType.append("Static (" + position.getLatitude() + "," + position.getLongitude() + ") ");
        }
        if (filterDistance(position, last) && !skipLimit(position, last) && !skipAttributes(position)) {
            filterType.append("Distance ");
        }
        if (filterMaxSpeed(position, last)) {
            filterType.append("MaxSpeed ");
        }
        if (filterMinPeriod(position, last)) {
            filterType.append("MinPeriod ");
        }

        if (filterType.length() > 0) {

            StringBuilder message = new StringBuilder();
            message.append("Position filtered by ");
            message.append(filterType.toString());
            message.append("filters from device: ");
            message.append(Context.getIdentityManager().getById(position.getDeviceId()).getUniqueId());

            LOGGER.info(message.toString());

            if (filterType.indexOf("Zero") != -1 || !position.getValid()) {
                return false;
            }
            return true;
        }

        return false;
    }

    @Override
    protected Position handlePosition(Position position) {
        if (filter(position)) {
            return null;
        }
        if (filterZero(position) || !position.getValid()) {
            Position last = null;
            if (Context.getIdentityManager() != null) {
                last = Context.getIdentityManager().getLastPosition(position.getDeviceId());
                if (last != null) {
                    Map<String, Object> attr = new HashMap<>();
                    attr.put("ZeroLocation", true);
                    attr.put("PreviousLocation", true);
                    attr.putAll(last.getAttributes());

                    position.setAttributes(attr);
                    position.setServerTime(last.getServerTime());
                    position.setDeviceTime(last.getDeviceTime());
                    clonePosition(position, last);
                    position.setValid(true);
                    position.setAddress(last.getAddress() == null ? "" : last.getAddress());
                    position.setAccuracy(last.getAccuracy());
                    position.setNetwork(last.getNetwork());
                } else {
                    Map<String, Object> attr = new HashMap<>();
                    attr.put("ZeroLocation", true);
                    attr.put("PreviousLocation", true);
                    attr.put("ZeroPosition", "Head Office");
                    attr.putAll(position.getAttributes());
                    position.setAttributes(attr);
                    position.setServerTime(new Date());
                    position.setDeviceTime(new Date());
                    position.setFixTime(new Date());
                    position.setValid(true);
                    position.setLatitude(9.018015);
                    position.setLongitude(38.795576);
                    position.setAltitude(0.0);
                    position.setSpeed(0.0);
                    position.setCourse(0.0);
                }
            }
        }
        return position;
    }

    public static void clonePosition(Position position, Position last) {
        position.setFixTime(last.getFixTime());
        position.setValid(last.getValid());
        position.setLatitude(last.getLatitude());
        position.setLongitude(last.getLongitude());
        position.setAltitude(last.getAltitude());
        position.setSpeed(last.getSpeed());
        position.setCourse(last.getCourse());
    }

}
