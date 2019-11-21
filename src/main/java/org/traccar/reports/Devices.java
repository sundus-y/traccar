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
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
                "templates/export/devices.xlsx");
        exportDevices(outputStream, userId, devices, templatePath);
    }

    public static void getGroupExcel(
            OutputStream outputStream, long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws SQLException, IOException {
        Set<Long> deviceIdsList = new HashSet<Long>(ReportUtils.getDeviceList(deviceIds, groupIds));
        Collection<Device> devices = Context.getDeviceManager().getItems(deviceIdsList);

        String templatePath = Context.getConfig().getString("report.templatesPath",
                "templates/export/group_device_report.xlsx");
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm a");
        String reportDate = simpleDateFormat.format(new Date());
        AtomicReference<Boolean> hasMultipleGroups = new AtomicReference<>(false);
        String groupName = devices.iterator().next().getGroupName();
        devices.forEach((device -> {
            if (device.getGroupName().compareToIgnoreCase(groupName) != 0) {
                hasMultipleGroups.set(true);
            }
        }));
        if (hasMultipleGroups.get()) {
            throw new IllegalArgumentException("Devices from Multiple Group Selected.");
        } else {
            try (InputStream inputStream = new FileInputStream(templatePath)) {
                org.jxls.common.Context jxlsContext = ReportUtils.initializeContext(userId);
                jxlsContext.putVar("devices", devices);
                jxlsContext.putVar("device", devices.iterator().next());
                jxlsContext.putVar("groupName", devices.iterator().next().getGroupName());
                jxlsContext.putVar("devicesCount", devices.size());
                jxlsContext.putVar("reportDate", reportDate);
                JxlsHelper.getInstance().setUseFastFormulaProcessor(false)
                        .processTemplate(inputStream, outputStream, jxlsContext);
            }
        }
    }

    public static void getIndividualExcel(
            OutputStream outputStream, long userId, Device device) throws SQLException, IOException {

        String templatePath = Context.getConfig().getString("report.templatesPath",
                "templates/export/individual_device_report.xlsx");
        exportDevice(outputStream, userId, device, templatePath);
    }

    public static void getIndividualExcelZip(
            ByteArrayOutputStream byteArrayOutputStream, long userId,
            Collection<Device> devices) throws SQLException, IOException {
        ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
        for (Device device : devices) {
            final ByteArrayOutputStream oStream = new ByteArrayOutputStream();
            String templatePath = Context.getConfig().getString("report.templatesPath",
                    "templates/export/individual_device_report.xlsx");
            exportDevice(oStream, userId, device, templatePath);
            ZipEntry zipEntry = new ZipEntry(device.getUniqueId()
                    .replaceAll("[^a-zA-Z0-9.\\-]", "_") + "_Device_Report.xlsx");
            zipOutputStream.putNextEntry(zipEntry);
            zipOutputStream.write(oStream.toByteArray());
            zipOutputStream.closeEntry();
        }
        zipOutputStream.close();
    }

    private static void exportDevices(
            OutputStream outputStream, long userId, Collection<Device> devices,
            String templatePath) throws IOException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm a");
        String reportDate = simpleDateFormat.format(new Date());
        try (InputStream inputStream = new FileInputStream(templatePath)) {
            org.jxls.common.Context jxlsContext = ReportUtils.initializeContext(userId);
            jxlsContext.putVar("devices", devices);
            jxlsContext.putVar("reportDate", reportDate);
            JxlsHelper.getInstance().setUseFastFormulaProcessor(false)
                    .processTemplate(inputStream, outputStream, jxlsContext);
        }
    }

    private static void exportDevice(
            OutputStream outputStream, long userId, Device device,
            String templatePath) throws IOException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm a");
        String reportDate = simpleDateFormat.format(new Date());
        try (InputStream inputStream = new FileInputStream(templatePath)) {
            org.jxls.common.Context jxlsContext = ReportUtils.initializeContext(userId);
            jxlsContext.putVar("device", device);
            jxlsContext.putVar("reportDate", reportDate);
            JxlsHelper.getInstance().setUseFastFormulaProcessor(false)
                    .processTemplate(inputStream, outputStream, jxlsContext);
        }
    }
}
