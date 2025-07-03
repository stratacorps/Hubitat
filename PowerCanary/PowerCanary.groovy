/**
 * Canary Power Monitor
 *
 * MIT License - see full license in repository LICENSE file
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Description: Allows the monitoring of selected devices that have powerSource options available to determine
 * if a power outage may have occurred. Note that it is obvious that if your Hubitat hub is not on a battery
 * backup this is of no use. Many Zwave externders have battery backup and will report powerSource changes
 * from "mains" to "battery" when power is lost. If your hub is on a different power source, we can capture
 * this event for reporting, logging, and later integration with device restoration.
 *
 * Developed with OpenAI ChatGPT
 *
 * Versions:
 * 1.0.0 (2025-07-03) - Initial testing application.
 */

//App metadata
preferences {
    section("Virtual Device Management") {
        input name: "useCustomSwitch", type: "bool", title: "Use an existing virtual switch instead of auto-created one?", defaultValue: false
        input name: "customSwitch", type: "capability.switch", title: "Select existing virtual switch", required: false, multiple: false
    }
    section("Child Device Info") {
        paragraph "This app creates or uses a virtual switch named 'Canary Power Status' to reflect power status. It is listed as a child device under this app."
    }
    section("Maintenance Options") {
        input name: "clearLogButton", type: "bool", title: "Clear Outage History Log Now?", defaultValue: false
        input name: "exportLogButton", type: "bool", title: "Export Outage Log to Logs Now?", defaultValue: false
    }
    section("History Options") {
        input "maxOutageLogSize", "enum", title: "Maximum outage history entries to keep", options: ["5", "10", "25", "50"], defaultValue: "10"
    }
    section("Canary Devices (must support powerSource)") {
        input "canaryDevices", "capability.powerSource", title: "Select Canary Devices", multiple: true, required: true
    }
    section("Power Outage Logic") {
        input "triggerMode", "enum", title: "Consider power out if...", options: ["ANY", "ALL"], defaultValue: "ANY", required: true
    }
    section("Notification Options") {
        input "notifyDevice", "capability.notification", title: "Send push notification to", required: false, multiple: true
        input "notifyOnOutage", "bool", title: "Notify when power goes out?", defaultValue: true
        input "notifyOnRestore", "bool", title: "Notify when power is restored?", defaultValue: true
        input "outageMessage", "text", title: "Power Outage Message", defaultValue: "\u26A0\uFE0F Power outage detected (Canary Power Monitor)", required: false
        input "restoreMessage", "text", title: "Power Restore Message", defaultValue: "\uD83C\uDFE0 Power restored (Canary Power Monitor)", required: false
        input "limitOutageNotifications", "bool", title: "Limit outage notifications until power is restored?", defaultValue: true
        input "enableLogging", "bool", title: "Enable debug logging?", defaultValue: true
        input "enableTimestampLog", "bool", title: "Enable timestamp event logging to virtual switch?", defaultValue: true
    }
}

def stateOutageNotified = false

def getDeviceById(id) {
    return id ? location.devices.find { it.id == id } : null
}

def installed() {
    if (enableLogging) log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    if (enableLogging) log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def uninstalled() {
    getChildDevices()?.each {
        deleteChildDevice(it.deviceNetworkId)
        if (enableLogging) {
            log.info "Deleted child device: ${it.displayName}"
        }
    }
    if (enableLogging) {
        log.info "App uninstalled and child devices removed."
    }

    if (enableLogging) log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    if (clearLogButton) {
        state.outageLog = []
        if (enableLogging) log.info "Outage history log cleared by user."
    }
    if (exportLogButton && state.outageLog) {
        log.info "Exporting ${state.outageLog.size()} outage log entries:"
        state.outageLog.eachWithIndex { entry, i ->
            log.info "[${i + 1}] Start: ${entry.start}, End: ${entry.end}, Duration: ${entry.duration}"
        }
    }

    // Subscribe to all powerSource changes
    canaryDevices.each { device ->
        subscribe(device, "powerSource", powerSourceHandler)
    }

    // Use existing virtual switch or create new one
    if (useCustomSwitch && customSwitch) {
        state.virtualSwitchId = customSwitch.id
        if (enableLogging) log.debug "Using user-selected virtual switch: ${customSwitch.displayName}"
    } else {
        def child = getChildDevice("canaryPowerStatus")
        if (!child) {
            addChildDevice("hubitat", "Virtual Switch", "canaryPowerStatus", [
                name: "Canary Power Status",
                label: "Canary Power Status",
                isComponent: true
            ])
            if (enableLogging) log.debug "Created virtual switch: Canary Power Status"
        }
        def vs = getChildDevice("canaryPowerStatus")
        state.virtualSwitchId = vs?.id
    }

    checkPowerStatus()
}
    if (clearLogButton) {
        state.outageLog = []
        if (enableLogging) log.info "Outage history log cleared by user."
    }
    if (exportLogButton && state.outageLog) {
        log.info "Exporting ${state.outageLog.size()} outage log entries:"
        state.outageLog.eachWithIndex { entry, i ->
            log.info "[${i + 1}] Start: ${entry.start}, End: ${entry.end}, Duration: ${entry.duration}"
        }
    }
    // Subscribe to all powerSource changes
    canaryDevices.each { device ->
        subscribe(device, "powerSource", powerSourceHandler)
    }

    // Create virtual device if not already created
    def child = getChildDevice("canaryPowerStatus")
    if (!child) {
        addChildDevice("hubitat", "Virtual Switch", "canaryPowerStatus", [
            name: "Canary Power Status",
            label: "Canary Power Status",
            isComponent: true
        ])
        if (enableLogging) log.debug "Created virtual switch: Canary Power Status"
    }

    checkPowerStatus()
}

def powerSourceHandler(evt) {
    if (enableLogging) log.debug "Power source changed: ${evt.device.displayName} = ${evt.value}"
    checkPowerStatus()
}

def logTimestampEvent(message) {
    if (!enableTimestampLog) return
    def virtualSwitch = getChildDevice("canaryPowerStatus")
    if (virtualSwitch) {
        def nowStr = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
        def fullMsg = "[${nowStr}] ${message}"
        sendEvent(name: "canaryPowerLog", value: fullMsg, descriptionText: fullMsg, displayed: true)
    }
}

def checkPowerStatus() {
    def virtualSwitch = state.virtualSwitchId ? getDeviceById(state.virtualSwitchId) : null
    if (!virtualSwitch) {
        // already handled earlier
        return
    }
    def mainsDevices = canaryDevices.findAll {
        it.currentValue("powerSource") == "mains"
    }

    def isPowerOn = triggerMode == "ANY" ?
        mainsDevices.size() > 0 :
        mainsDevices.size() == canaryDevices.size()

    def virtualSwitch = getChildDevice("canaryPowerStatus")
    if (virtualSwitch) {
        if (isPowerOn && virtualSwitch.currentValue("switch") != "on") {
            virtualSwitch.on()
            if (enableLogging) log.info "Power restored (switch ON)"
            logTimestampEvent("Power restored")
            def restoreTime = now()
            if (state.outageStartTime) {
                def outageDuration = (restoreTime - state.outageStartTime) / 1000
                def durationText = "Outage duration: ${Math.round(outageDuration / 60)} min ${outageDuration % 60} sec"
                sendEvent(name: "lastOutageDuration", value: durationText, descriptionText: durationText, displayed: true)
                                def outageRecord = [
                    start: new Date(state.outageStartTime).format("yyyy-MM-dd HH:mm:ss", location.timeZone),
                    end: new Date(restoreTime).format("yyyy-MM-dd HH:mm:ss", location.timeZone),
                    duration: durationText
                ]
                if (!state.outageLog) state.outageLog = []
                state.outageLog.add(0, outageRecord)
                def limit = Integer.parseInt(maxOutageLogSize ?: "10")
                if (state.outageLog.size() > limit) {
                    state.outageLog = state.outageLog.take(limit)
                }
                def summaryText = "${outageRecord.start} to ${outageRecord.end} (${outageRecord.duration})"
                sendEvent(name: "lastOutageSummary", value: summaryText, descriptionText: summaryText, displayed: true)

                state.outageStartTime = null
            }
            sendEvent(name: "lastRestoreTime", value: new Date(restoreTime).format("yyyy-MM-dd HH:mm:ss", location.timeZone), displayed: true)
            if (notifyDevice && notifyOnRestore) {
                notifyDevice.each {
                    it.deviceNotification(restoreMessage ?: "\uD83C\uDFE0 Power restored (Canary Power Monitor)")
                }
            }
            state.outageNotified = false
        } else if (!isPowerOn && virtualSwitch.currentValue("switch") != "off") {
            virtualSwitch.off()
            if (enableLogging) log.info "Power outage detected (switch OFF)"
            logTimestampEvent("Power outage detected")
            state.outageStartTime = now()
            sendEvent(name: "lastOutageTime", value: new Date(state.outageStartTime).format("yyyy-MM-dd HH:mm:ss", location.timeZone), displayed: true)
            if (notifyDevice && notifyOnOutage) {
                if (!limitOutageNotifications || !state.outageNotified) {
                    notifyDevice.each {
                        it.deviceNotification(outageMessage ?: "\u26A0\uFE0F Power outage detected (Canary Power Monitor)")
                    }
                    state.outageNotified = true
                } else {
                    if (enableLogging) log.debug "Outage notification already sent, skipping."
                }
            }
        }
    } else {
        log.warn "Virtual switch not found"
    }
}
