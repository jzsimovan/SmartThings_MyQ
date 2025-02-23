/**
 * -----------------------
 * --- DEVICE HANDLER ----
 * -----------------------
 *
 *  MyQ Garage Door Opener
 *
 *  Copyright 2019 Brian Beaird
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 */
metadata {
	definition (name: "MyQ Lock Door", namespace: "brbeaird", author: "Brian Beaird", vid: "generic-contact-4",  mnmn: "SmartThings") {

		capability "Contact Sensor"
		capability "Refresh"
		capability "Polling"
		capability "Actuator"
		capability "Sensor"
        capability "Lock"
        //capability "Health Check"

		attribute "lastActivity", "string"
        attribute "doorSensor", "string"
        attribute "doorMoving", "string"
        attribute "OpenButton", "string"
        attribute "CloseButton", "string"
        attribute "dhName", "string"
		attribute "myQDeviceId", "string"

		command "updateDeviceStatus", ["string"]
		command "updateDeviceLastActivity", ["number"]
        command "updateDeviceMoving", ["string"]
        command "lock"
        command "unlock"
		command "updateMyQDeviceId", ["string"]
	}

	simulator {	}

	tiles {

		multiAttributeTile(name:"door", type: "lighting", width: 6, height: 4, canChangeIcon: false) {
			tileAttribute ("device.door", key: "PRIMARY_CONTROL") {
				attributeState "unknown", label:'${name}', icon:"st.doors.garage.garage-closed",    backgroundColor:"#ffa81e", nextState: "closing"
				attributeState "closed",  label:'${name}', action:"door control.open",   icon:"st.doors.garage.garage-closed",  backgroundColor:"#00a0dc"
				attributeState "open",    label:'${name}', action:"door control.close",  icon:"st.doors.garage.garage-open",    backgroundColor:"#e86d13"
				attributeState "opening", label:'${name}', 								 icon:"st.doors.garage.garage-opening", backgroundColor:"#cec236"
				attributeState "closing", label:'${name}', 								 icon:"st.doors.garage.garage-closing", backgroundColor:"#cec236"
				attributeState "waiting", label:'${name}', 								 icon:"st.doors.garage.garage-closing", backgroundColor:"#cec236"
				attributeState "stopped", label:'${name}', action:"door control.close",  icon:"st.doors.garage.garage-closing", backgroundColor:"#1ee3ff"
			}
            tileAttribute("device.lastActivity", key: "SECONDARY_CONTROL") {
        		attributeState("lastActivity", label:'Last Activity: ${currentValue}', defaultState: true)
    		}
		}

		standardTile("refresh", "device.door", width: 3, height: 2, decoration: "flat") {
			state("default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh")
		}
		standardTile("contact", "device.contact") {
			state("open", label:'${name}', icon:"st.contact.contact.open", backgroundColor:"#e86d13")
			state("closed", label:'${name}', icon:"st.contact.contact.closed", backgroundColor:"#00a0dc")
		}
        standardTile("openBtn", "device.OpenButton", width: 3, height: 3) {
            state "normal", label: 'Open', icon: "st.doors.garage.garage-open", backgroundColor: "#e86d13", action: "open", nextState: "opening"
            state "opening", label: 'Opening', icon: "st.doors.garage.garage-opening", backgroundColor: "#cec236", action: "open"
		}
        standardTile("closeBtn", "device.CloseButton", width: 3, height: 3) {
            state "normal", label: 'Close', icon: "st.doors.garage.garage-closed", backgroundColor: "#00a0dc", action: "close", nextState: "closing"
            state "closing", label: 'Closing', icon: "st.doors.garage.garage-closing", backgroundColor: "#cec236", action: "close"
		}
        valueTile("doorSensor", "device.doorSensor", width: 3, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:'${currentValue}', backgroundColor:"#ffffff"
		}
		valueTile("doorMoving", "device.doorMoving", width: 6, height: 2, inactiveLavel: false, decoration: "flat") {
			state "default", label: '${currentValue}', backgroundColor:"#ffffff"
		}
        main "door"
		details(["door", "openBtn", "closeBtn", "doorSensor", "refresh"])
	}
}


def push() {
	def doorState = device.currentState("door")?.value
	if (doorState == "open" || doorState == "stopped") {
		close()
	} else if (doorState == "closed") {
		open()
	}
	sendEvent(name: "momentary", value: "pushed", display: false, displayed: false, isStateChange: true)
}

def open()  {
	log.debug "Garage door open command called."
    parent.notify("Garage door open command called.")
    updateDeviceStatus("opening")
    parent.sendCommand(getMyQDeviceId(), "open")

    runIn(20, refresh, [overwrite: true])	//Force a sync with tilt sensor after 20 seconds
}
def close() {
	log.debug "Garage door close command called."
    parent.notify("Garage door close command called.")
	parent.sendCommand(getMyQDeviceId(), "close")
//	updateDeviceStatus("closing")			// Now handled in the parent (in case we have an Acceleration sensor, we can handle "waiting" state)
    runIn(30, refresh, [overwrite: true]) //Force a sync with tilt sensor after 30 seconds
}

def refresh() {
    parent.refresh(this)
}

def poll() { refresh() }

def lock(){
	log.debug "locked"
    close()
    sendEvent(name: "lock", value: "locked", display: false, displayed: false, isStateChange: true)
}

def unlock(){
	log.debug "unlocked"
    open()
    sendEvent(name: "lock", value: "unlocked", display: false, displayed: false, isStateChange: true)
}

// update status
def updateDeviceStatus(status) {

    def currentState = device.currentState("door")?.value
    def lockState
    if (device.currentState("lock")?.value == "locked"){lockState = "closed"}
    if (device.currentState("lock")?.value == "unlocked"){lockState = "open"}

    log.debug "Request received to update door status to : " + status

    //Don't do anything if nothing changed
    if (currentState == status && lockState != status){
    	log.debug "No change; door is already set to " + status
        status = ""
    }

    switch (status) {
		case "open":
    		log.debug "Door is now open"
			sendEvent(name: "door", value: "open", display: true, isStateChange: true, descriptionText: device.displayName + " is open")
			sendEvent(name: "contact", value: "open", display: false, displayed: false, isStateChange: true)	// make sure we update the hidden states as well
            sendEvent(name: "lock", value: "unlocked", display: false, displayed: false, isStateChange: true)		// unlocked == open
            break

        case "closed":
			log.debug "Door is now closed"
        	sendEvent(name: "door", value: "closed", display: true, isStateChange: true, descriptionText: device.displayName + " is closed")
			sendEvent(name: "contact", value: "closed", display: false, displayed: false, isStateChange: true)	// update hidden states
            sendEvent(name: "lock", value: "locked", display: false, displayed: false, isStateChange: true)		// locked == closed
            break

		case "opening":
			if (currentState == "open"){
        		log.debug "Door is already open. Leaving status alone."
        	}
        	else{
        		sendEvent(name: "door", value: "opening", descriptionText: "Sent opening command.", display: false, displayed: true, isStateChange: true)
        	}
            break

		case "closing":
    		if(currentState == "closed"){
        		log.debug "Door is already closed. Leaving status alone."
        	}
			else{
        		sendEvent(name: "door", value: "closing", display: false, displayed: false, isStateChange: true)
        	}
            break

    	case "stopped":
    		if (currentState != "closed") {
    			log.debug "Door is stopped"
    			sendEvent(name: "door", value: "stopped", display: false, displayed: false, isStateChange: true)
        	}
            break

        case "waiting":
        	if (currentState == "open") {
            	log.debug "Door is waiting before closing"
                sendEvent(name: "door", value: "waiting", display: false, displayed: false, isStateChange: true)
            }
            break
        }
}

def updateDeviceLastActivity(lastActivity) {
	def finalString = lastActivity?.format('MM/d/yyyy hh:mm a',location.timeZone)
	sendEvent(name: "lastActivity", value: finalString, display: false , displayed: false)
}

def updateDeviceSensor(sensor) {
	sendEvent(name: "doorSensor", value: sensor, display: false , displayed: false)
}

def updateDeviceMoving(moving) {
	sendEvent(name: "doorMoving", value: moving, display: false , displayed: false)
}

def log(msg){
	log.debug msg
}

def showVersion(){
	return "3.1.0"
}

def getMyQDeviceId(){
    if (device.currentState("myQDeviceId")?.value)
    	return device.currentState("myQDeviceId").value
	else{
        def newId = device.deviceNetworkId.split("\\|")[2]
        sendEvent(name: "myQDeviceId", value: newId, display: true , displayed: true)
        return newId
    }
}

def updateMyQDeviceId(Id) {
	log.debug "Setting MyQID to ${Id}"
    sendEvent(name: "myQDeviceId", value: Id, display: true , displayed: true)
}


def parse(String description) {
	//log.trace "[DTH] Executing 'parse(String description)' for device ${device.displayName} with description = $description"

	def result = createEvent(descriptionText:description, isStateChange:true, displayed:false)
    sendEvent([name: "dhName", value: "Universal Z-Wave Lock Device Handler"]) // Save DH Name for parent app
	log.info "[DTH] parse() - returning result=$result"
	result
}