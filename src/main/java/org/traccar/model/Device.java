/*
 * Copyright 2012 - 2018 Anton Tananaev (anton@traccar.org)
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
package org.traccar.model;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.traccar.Context;
import org.traccar.database.DeviceManager;
import org.traccar.database.GroupsManager;
import org.traccar.database.QueryExtended;
import org.traccar.database.QueryIgnore;

public class Device extends GroupedModel {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String uniqueId;

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public static final String STATUS_UNKNOWN = "unknown";
    public static final String STATUS_ONLINE = "online";
    public static final String STATUS_OFFLINE = "offline";

    private String status;

    @QueryIgnore
    public String getStatus() {
        return status != null ? status : STATUS_OFFLINE;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    private Date lastUpdate;

    @QueryExtended
    public Date getLastUpdate() {
        if (lastUpdate != null) {
            return new Date(lastUpdate.getTime());
        } else {
            return null;
        }
    }

    public void setLastUpdate(Date lastUpdate) {
        if (lastUpdate != null) {
            this.lastUpdate = new Date(lastUpdate.getTime());
        } else {
            this.lastUpdate = null;
        }
    }

    private long positionId;

    @QueryIgnore
    public long getPositionId() {
        return positionId;
    }

    public void setPositionId(long positionId) {
        this.positionId = positionId;
    }

    private List<Long> geofenceIds;

    @QueryIgnore
    public List<Long> getGeofenceIds() {
        return geofenceIds;
    }

    public void setGeofenceIds(List<Long> geofenceIds) {
        this.geofenceIds = geofenceIds;
    }

    private String phone;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    private String model;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    private String contact;

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    private String category;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    private boolean disabled;

    public boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    private String plateNumber;

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    private String vehicleModel;

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String vehicleModel) {
        this.vehicleModel = vehicleModel;
    }

    private Date membershipDate;

    public Date getMembershipDate() {
        if (membershipDate != null) {
            return new Date(membershipDate.getTime());
        } else {
            return null;
        }
    }

    public void setMembershipDate(Date membershipDate) {
        if (membershipDate != null) {
            this.membershipDate = new Date(membershipDate.getTime());
        } else {
            this.membershipDate = null;
        }
    }

    private Date membershipRenewalDate;

    public Date getMembershipRenewalDate() {
        if (membershipRenewalDate != null) {
            return new Date(membershipRenewalDate.getTime());
        } else {
            return null;
        }
    }

    public void setMembershipRenewalDate(Date membershipRenewalDate) {
        if (membershipRenewalDate != null) {
            this.membershipRenewalDate = new Date(membershipRenewalDate.getTime());
        } else {
            this.membershipRenewalDate = null;
        }
    }

    private String newPlateNumber;

    public String getNewPlateNumber() {
        return newPlateNumber;
    }

    public void setNewPlateNumber(String newPlateNumber) {
        this.newPlateNumber = newPlateNumber;
    }

    private String gender;

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    private String countryOfManufacturing;

    public String getCountryOfManufacturing() {
        return countryOfManufacturing;
    }

    public void setCountryOfManufacturing(String countryOfManufacturing) {
        this.countryOfManufacturing = countryOfManufacturing;
    }

    private String manufacturingYear;

    public String getManufacturingYear() {
        return manufacturingYear;
    }

    public void setManufacturingYear(String manufacturingYear) {
        this.manufacturingYear = manufacturingYear;
    }

    private String engineNumber;

    public String getEngineNumber() {
        return engineNumber;
    }

    public void setEngineNumber(String engineNumber) {
        this.engineNumber = engineNumber;
    }

    private String chassisNumber;

    public String getChassisNumber() {
        return chassisNumber;
    }

    public void setChassisNumber(String chassisNumber) {
        this.chassisNumber = chassisNumber;
    }

    private String simNumber;

    public String getSimNumber() {
        return simNumber;
    }

    public void setSimNumber(String simNumber) {
        this.simNumber = simNumber;
    }

    private String simIccidNumber;

    public String getSimIccidNumber() {
        return simIccidNumber;
    }

    public void setSimIccidNumber(String simIccidNumber) {
        this.simIccidNumber = simIccidNumber;
    }

    private String groupName;

    @QueryIgnore
    public String getGroupName() {
        GroupsManager groupManager = Context.getGroupsManager();
        Group group = groupManager.getById(getGroupId());
        return group == null ? "" : group.getName();
    }

    private String lastLocation;

    @QueryIgnore
    public String getLastLocation() {
        DeviceManager deviceManager = Context.getDeviceManager();
        Position position = deviceManager.getLastPosition(getId());
        return position == null ? "" : position.toString();
    }

    private String registrationSubCity;

    public String getRegistrationSubCity() {
        return this.registrationSubCity;
    }

    public void setRegistrationSubCity(String registrationSubCity) {
        this.registrationSubCity = registrationSubCity;
    }

    private String onlineStatus;

    @QueryIgnore
    public String getOnlineStatus() {
        if (lastUpdate != null) {
            Date now =  new Date();
            Date lastUp = new Date(lastUpdate.getTime());
            long diffInMill = now.getTime() - lastUpdate.getTime();
            long diff = TimeUnit.DAYS.convert(diffInMill, TimeUnit.MILLISECONDS);
            return (diff <= 1) ? "Online" : "Offline Since " + diff + " Days.";
        } else {
            return "--";
        }
    }

    private String finalPlateNumber;

    @QueryIgnore
    public String getFinalPlateNumber() {
        if (newPlateNumber != null && newPlateNumber != "") {
            return newPlateNumber;
        } else {
            return plateNumber;
        }
    }
}
