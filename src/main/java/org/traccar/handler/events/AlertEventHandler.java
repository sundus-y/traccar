/*
 * Copyright 2016 - 2019 Anton Tananaev (anton@traccar.org)
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
package org.traccar.handler.events;

import java.util.Collections;
import java.util.Map;

import io.netty.channel.ChannelHandler;
import org.traccar.Context;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.IdentityManager;
import org.traccar.model.Event;
import org.traccar.model.Position;

@ChannelHandler.Sharable
public class AlertEventHandler extends BaseEventHandler {

    private final IdentityManager identityManager;
    private final boolean ignoreDuplicateAlerts;

    public AlertEventHandler(Config config, IdentityManager identityManager) {
        this.identityManager = identityManager;
        ignoreDuplicateAlerts = config.getBoolean(Keys.EVENT_IGNORE_DUPLICATE_ALERTS);
    }

    @Override
    protected Map<Event, Position> analyzePosition(Position position) {
        Object alarm = position.getAttributes().get(Position.KEY_ALARM);
        Position lastPosition = identityManager.getLastPosition(position.getDeviceId());
        if (alarm != null) {
            boolean ignoreAlert = false;
            if (ignoreDuplicateAlerts) {
                if (lastPosition != null && alarm.equals(lastPosition.getAttributes().get(Position.KEY_ALARM))) {
                    ignoreAlert = true;
                }
            }
            if (!ignoreAlert) {
                Event event = new Event(Event.TYPE_ALARM, position.getDeviceId(), position.getId());
                event.set(Position.KEY_ALARM, (String) alarm);
                return Collections.singletonMap(event, position);
            }
        } else if (lastPosition != null && (lastPosition.getSpeed() - position.getSpeed() > 40)) {
            Event event = new Event(Event.TYPE_ALARM, position.getDeviceId(), position.getId());
            event.set(Position.KEY_ALARM, Position.ALARM_BRAKING);
            event.set("currentSpeed", position.getSpeed());
            event.set("previousSpeed", lastPosition.getSpeed());
            Context.getNotificationManager().updateEvent(event, position);

            event = new Event(Event.TYPE_ALARM, position.getDeviceId(), position.getId());
            event.set(Position.KEY_ALARM, Position.ALARM_ACCIDENT);
            event.set("currentSpeed", position.getSpeed());
            event.set("previousSpeed", lastPosition.getSpeed());
            Context.getNotificationManager().updateEvent(event, position);

            return Collections.singletonMap(event, position);
        }
        return null;
    }

}
