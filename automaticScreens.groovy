/**
 *  Automatic Screens
 *
 *  Copyright 2018 Nassere Besseghir
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
 */
definition(
    name: "Automatic Screens",
    namespace: "NassereB",
    author: "Nassere Besseghir",
    description: "automatic control for fully optimised sun shading",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png") {
    appSetting "WeatherApiKey"
}

////////////////////////////
// Preferences and Settings
////////////////////////////

preferences {
    page(name: "otherPage")
    page(name: "pageConfigureBlinds")
    page(name: "pageForecastIO")
}

def otherPage() {
    dynamicPage(name: "otherPage") {
        section("Turn on when motion detected:") {
            input "themotion", "capability.motionSensor", required: true, title: "Where?"
        }
        section("Screen:") {
            input "thescreen", "capability.windowShade", required: true, title: "Which screens?", multiple: true, submitOnChange: true
            if (thescreen) thescreen.each {
                href "pageConfigureBlinds", title: "Configure ${it.name}", description: "Tap to open", params: it
            }
        }
        section("Info Page") {
            href "pageForecastIO", title: "Environment Info", description: "Tap to open"
        }
    }
}

def pageConfigureBlinds(dev) {
    def pageProperties = [
        name: "pageConfigureBlinds",
        title: "Configure for ${dev.name}",
        nextPage: "pageSetupForecastIO",
        uninstall: false
    ]

    return dynamicPage(pageProperties) {
        thescreen.each {
            log.debug "it.name: ${it.name}"
            log.debug "it: ${it}"
            if (it.name == dev.name) {
                def devId = it.id
                def devType = it.typeName
                def blindOptions = ["Down", "Up"]

                if (it.hasCommand("presetPosition")) blindOptions.add("Preset")
                if (it.hasCommand("stop")) blindOptions.add("Stop")

                def blind = it.currentValue("somfySupported")
                if (blind == 'true') {
                    blind = true
                } else {
                    blind = false
                }

                section(it.name) {
                    paragraph image: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
                        title: "paragraph title",
                        required: true,
                        "Orientation of the blinds relative to true (geographic) north (0 = North, 90 = east, 180 = south, 270 = West)"
                    input "orientation_${devId}", "number", required: false, title: "Orientation",
                        default: 0
                    input "directionalTolerance_${devId}", "number", required: false, title: "Directional tolerance",
                        default: 45
                    input "directionalToleranceOtherDirection_${devId}", "number", required: false, title: "Directional tolerance in the other direction",
                        default: 45
                    input "closeMaxAction_${devId}", "enum", title: "Action to take", options: blindOptions
                    input "cloudCover_${devId}", "number", title: "Protect until what cloudcover% (0=clear sky)", range: "0..100", multiple: false, required: false,
                        default: 30
                }
            }
        }
    }
}

def pageForecastIO() {
    def sunPosition = getSunPosition(location.latitude, location.longitude, new Date())

    def pageProperties = [
        name: "pageForecastIO",
        title: "Current Sun Info",
        nextPage: "otherPage",
        refreshInterval: 10,
        uninstall: false
    ]

    return dynamicPage(pageProperties) {
        section("Hub Location") {
            paragraph "Latitude ${location.latitude}"
            paragraph "Longitude ${location.longitude}"
        }
        section("Sun Position") {
            //paragraph "Suncoord ${state.c}"

            paragraph "Azimuth ${sunPosition.azimuth}"
            paragraph "Altitude ${sunPosition.altitude}"
        }
    }
}








def installed() {
    log.debug "Installed with settings: ${settings}"
    log.debug "Location: ${location.latitude}"
    log.debug "Location: ${location}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def initialize() {
    // TODO: subscribe to attributes, devices, locations, etc.
    subscribe(themotion, "motion.active", motionDetectedHandler)

    runEvery1Minute(checkForSun)
}

//////////////////////
// Event handlers
//////////////////////

def motionDetectedHandler(evt) {
    log.debug "motionDetectedHandler called: $evt"
    // thescreen.open()
}

def checkForSun(evt) {
    log.debug "checkForSun called at ${new Date()}"
}



/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Azimuth and Alitude for current Time
/*-----------------------------------------------------------------------------------------*/
def getSunPosition(latitude, logitude, currentDateTime) {
    state.lat = location.latitude
    state.lng = location.longitude
    state.julian = toJulian(currentDateTime)

    def lw = rad() * -location.longitude
    state.lw = lw

    def phi = rad() * location.latitude
    state.phi = phi

    def d = toDays(currentDateTime)
    state.d = d

    def c = sunCoords(d)
    state.c = c

    def H = siderealTime(d, lw) - c.ra
    state.H = H

    def az = azimuth(H, phi, c.dec)
    az = (az * 180 / Math.PI) + 180
    def al = altitude(H, phi, c.dec)
    al = al * 180 / Math.PI

    return [
        azimuth: az,
        altitude: al
    ]
}
/*-----------------------------------------------------------------------------------------*/
/*	Return the Julian date 
/*-----------------------------------------------------------------------------------------*/
def toJulian(date) {
    return date.getTime() / dayMs() - 0.5 + J1970() // ms time/ms in a day = days - 0.5 + number of days 1970.... 
}
/*-----------------------------------------------------------------------------------------*/
/*	Return the number of days since J2000
/*-----------------------------------------------------------------------------------------*/
def toDays(date) {
    return toJulian(date) - J2000()
}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun RA
/*-----------------------------------------------------------------------------------------*/
def rightAscension(l, b) {
    return Math.atan2(Math.sin(l) * Math.cos(e()) - Math.tan(b) * Math.sin(e()), Math.cos(l))
}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Declination
/*-----------------------------------------------------------------------------------------*/
def declination(l, b) {
    return Math.asin(Math.sin(b) * Math.cos(e()) + Math.cos(b) * Math.sin(e()) * Math.sin(l))
}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Azimuth
/*-----------------------------------------------------------------------------------------*/
def azimuth(H, phi, dec) {
    return Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi))
}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Altitude
/*-----------------------------------------------------------------------------------------*/
def altitude(H, phi, dec) {
    return Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H))
}
/*-----------------------------------------------------------------------------------------*/
/*	compute sidereal time (One sidereal day corresponds to the time taken for the Earth to rotate once with respect to the stars and lasts approximately 23 h 56 min.
/*-----------------------------------------------------------------------------------------*/
def siderealTime(d, lw) {
    return rad() * (280.16 + 360.9856235 * d) - lw
}
/*-----------------------------------------------------------------------------------------*/
/*	Compute Sun Mean Anomaly
/*-----------------------------------------------------------------------------------------*/
def solarMeanAnomaly(d) {
    return rad() * (357.5291 + 0.98560028 * d)
}
/*-----------------------------------------------------------------------------------------*/
/*	Compute Sun Ecliptic Longitude
/*-----------------------------------------------------------------------------------------*/
def eclipticLongitude(M) {
    def C = rad() * (1.9148 * Math.sin(M) + 0.02 * Math.sin(2 * M) + 0.0003 * Math.sin(3 * M))
    def P = rad() * 102.9372 // perihelion of the Earth
    return M + C + P + Math.PI
}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Coordinates
/*-----------------------------------------------------------------------------------------*/
def sunCoords(d) {
    def M = solarMeanAnomaly(d)
    def L = eclipticLongitude(M)
    return [dec: declination(L, 0), ra: rightAscension(L, 0)]
}
/*-----------------------------------------------------------------------------------------*/
/*	Some auxilliary routines for readabulity in the code
/*-----------------------------------------------------------------------------------------*/
def dayMs() {
    return 1000 * 60 * 60 * 24
}
def J1970() {
    return 2440588
}
def J2000() {
    return 2451545
}
def rad() {
    return Math.PI / 180
}
def e() {
    return rad() * 23.4397
}
