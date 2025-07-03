metadata {
    definition(name: "Canary Power Status", namespace: "Brackley", author: "Kevin Brackley") {
        capability "Switch"
        capability "Actuator"

        attribute "lastOutageTime", "string"
        attribute "lastRestoreTime", "string"
        attribute "lastOutageDuration", "string"
        attribute "lastOutageSummary", "string"
        attribute "totalOutages", "number"
        attribute "totalOutageDuration", "string"
        attribute "canaryPowerLog", "string"

        command "resetStats"
    }
}

def installed() {
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "totalOutages", value: 0)
    sendEvent(name: "totalOutageDuration", value: "00:00:00")
}

def updated() {
    log.debug "Device updated"
}

def parse(String description) {
    log.debug "Parsing '${description}'"
}

def on() {
    sendEvent(name: "switch", value: "on")
}

def off() {
    sendEvent(name: "switch", value: "off")
}

def resetStats() {
    sendEvent(name: "totalOutages", value: 0)
    sendEvent(name: "totalOutageDuration", value: "00:00:00")
    log.info "Canary Power Status statistics reset."
}
