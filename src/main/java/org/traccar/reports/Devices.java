/*
 * Copyright 2016 Anton Tananaev (anton@traccar.org)
 * Copyright 2016 Andrey Kunitsyn (andrey@traccar.org)
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
package org.traccar.reports;

import org.jxls.util.JxlsHelper;
import org.traccar.Context;
import org.traccar.database.DeviceManager;
import org.traccar.model.Device;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Set;

public final class Devices {

    private Devices() {
    }

    public static Collection<Device> getObjects(long userId) throws SQLException {
        DeviceManager deviceManager = Context.getDeviceManager();
        Context.getPermissionsManager().checkUser(userId, userId);
        Set<Long> result = null;
        if (Context.getPermissionsManager().getUserAdmin(userId)) {
            result = deviceManager.getAllUserItems(userId);
        } else {
            result = deviceManager.getUserItems(userId);
        }
        return deviceManager.getItems(result);
    }

    public static void getExcel(OutputStream outputStream, long userId)
            throws SQLException, IOException {
        Collection<Device> devices = getObjects(userId);
        String templatePath = Context.getConfig().getString("report.templatesPath",
                "templates/export/");
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm a");
        String reportDate = simpleDateFormat.format(new Date());
        try (InputStream inputStream = new FileInputStream(templatePath + "/devices.xlsx")) {
            org.jxls.common.Context jxlsContext = ReportUtils.initializeContext(userId);
            jxlsContext.putVar("devices", devices);
            jxlsContext.putVar("reportDate", reportDate);
            JxlsHelper.getInstance().setUseFastFormulaProcessor(false)
                    .processTemplate(inputStream, outputStream, jxlsContext);
        }
    }
}
