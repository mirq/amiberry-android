/*
 * floppybridge_stub.cpp - Stub FloppyBridge API for Android
 *
 * FloppyBridge is hardware-specific and not supported on Android.
 * This provides stub implementations to satisfy linker.
 */

#include "sysconfig.h"
#include "sysdeps.h"

#ifdef __ANDROID__

#include <vector>
#include "floppybridge_lib.h"

// Static functions - all return failure/empty
bool FloppyBridgeAPI::isAvailable()
{
    return false;
}

bool FloppyBridgeAPI::getBridgeDriverInformation(bool allowCheckForUpdates, BridgeInformation& bridgeInformation)
{
    memset(&bridgeInformation, 0, sizeof(bridgeInformation));
    _tcscpy(bridgeInformation.about, _T("FloppyBridge not available on Android"));
    _tcscpy(bridgeInformation.url, _T("https://amiga.robsmithdev.co.uk"));
    return false;
}

FloppyBridgeAPI* FloppyBridgeAPI::createDriver(unsigned int driverIndex)
{
    return nullptr;
}

FloppyBridgeAPI* FloppyBridgeAPI::createDriverFromString(const char* config)
{
    return nullptr;
}

FloppyBridgeAPI* FloppyBridgeAPI::createDriverFromProfileID(unsigned int profileID)
{
    return nullptr;
}

void FloppyBridgeAPI::getDriverList(std::vector<DriverInformation>& driverList)
{
    driverList.clear();
}

void FloppyBridgeAPI::enumCOMPorts(std::vector<const TCHAR*>& portList)
{
    portList.clear();
}

bool FloppyBridgeAPI::getAllProfiles(std::vector<FloppyBridgeProfileInformation>& profileList)
{
    profileList.clear();
    return false;
}

bool FloppyBridgeAPI::importProfilesFromString(const char* profilesString)
{
    return false;
}

bool FloppyBridgeAPI::exportProfilesToString(char** profilesString)
{
    return false;
}

bool FloppyBridgeAPI::getProfileConfigAsString(unsigned int profileID, char** config)
{
    return false;
}

bool FloppyBridgeAPI::setProfileConfigFromString(unsigned int profileID, const char* config)
{
    return false;
}

bool FloppyBridgeAPI::setProfileName(unsigned int profileID, const char* name)
{
    return false;
}

bool FloppyBridgeAPI::createNewProfile(unsigned int driverIndex, unsigned int* profileID)
{
    return false;
}

bool FloppyBridgeAPI::deleteProfile(unsigned int profileID)
{
    return false;
}

// Instance methods - minimal stubs
FloppyBridgeAPI::FloppyBridgeAPI(unsigned int driverIndex, BridgeDriverHandle handle)
    : m_handle(handle), m_driverIndex(driverIndex)
{
}

FloppyBridgeAPI::~FloppyBridgeAPI()
{
}

bool FloppyBridgeAPI::initialise()
{
    return false;
}

void FloppyBridgeAPI::shutdown()
{
}

const FloppyDiskBridge::BridgeDriver* FloppyBridgeAPI::getDriverInfo()
{
    return nullptr;
}

unsigned char FloppyBridgeAPI::getBitSpeed()
{
    return 0;
}

FloppyDiskBridge::DriveTypeID FloppyBridgeAPI::getDriveTypeID()
{
    return DriveTypeID::dti35DD;
}

bool FloppyBridgeAPI::isStillWorking()
{
    return false;
}

const char* FloppyBridgeAPI::getLastErrorMessage()
{
    return "FloppyBridge not available on Android";
}

bool FloppyBridgeAPI::resetDrive(int trackNumber)
{
    return false;
}

bool FloppyBridgeAPI::isAtCylinder0()
{
    return false;
}

unsigned char FloppyBridgeAPI::getMaxCylinder()
{
    return 0;
}

void FloppyBridgeAPI::gotoCylinder(int cylinderNumber, bool side)
{
}

void FloppyBridgeAPI::handleNoClickStep(bool side)
{
}

unsigned char FloppyBridgeAPI::getCurrentCylinderNumber()
{
    return 0;
}

bool FloppyBridgeAPI::isMotorRunning()
{
    return false;
}

void FloppyBridgeAPI::setMotorStatus(bool side, bool turnOn)
{
}

bool FloppyBridgeAPI::isReady()
{
    return false;
}

bool FloppyBridgeAPI::isDiskInDrive()
{
    return false;
}

bool FloppyBridgeAPI::hasDiskChanged()
{
    return false;
}

bool FloppyBridgeAPI::getCurrentSide()
{
    return false;
}

bool FloppyBridgeAPI::isMFMPositionAtIndex(int mfmPositionBits)
{
    return false;
}

bool FloppyBridgeAPI::isMFMDataAvailable()
{
    return false;
}

bool FloppyBridgeAPI::getMFMBit(const int mfmPositionBits)
{
    return false;
}

int FloppyBridgeAPI::getMFMSpeed(const int mfmPositionBits)
{
    return 0;
}

void FloppyBridgeAPI::mfmSwitchBuffer(bool side)
{
}

void FloppyBridgeAPI::setSurface(bool side)
{
}

int FloppyBridgeAPI::maxMFMBitPosition()
{
    return 0;
}

void FloppyBridgeAPI::writeShortToBuffer(bool side, unsigned int track, unsigned short mfmData, int mfmPosition)
{
}

int FloppyBridgeAPI::getMFMTrack(bool side, unsigned int track, bool resyncRotation, const int bufferSizeInBytes, void* output)
{
    return 0;
}

bool FloppyBridgeAPI::setDirectMode(bool directModeEnable)
{
    return false;
}

bool FloppyBridgeAPI::writeMFMTrackToBuffer(bool side, unsigned int track, bool writeFromIndex, int sizeInBytes, void* mfmData)
{
    return false;
}

bool FloppyBridgeAPI::isWriteProtected()
{
    return true;
}

unsigned int FloppyBridgeAPI::commitWriteBuffer(bool side, unsigned int track)
{
    return 0;
}

bool FloppyBridgeAPI::isWritePending()
{
    return false;
}

bool FloppyBridgeAPI::isWriteComplete()
{
    return true;
}

bool FloppyBridgeAPI::canTurboWrite()
{
    return false;
}

bool FloppyBridgeAPI::isReadyToWrite()
{
    return false;
}

const unsigned int FloppyBridgeAPI::getDriverTypeIndex() const
{
    return m_driverIndex;
}

bool FloppyBridgeAPI::getConfigAsString(char** config) const
{
    return false;
}

bool FloppyBridgeAPI::setConfigFromString(const char* config) const
{
    return false;
}

bool FloppyBridgeAPI::getDriverIndex(int& driverIndex) const
{
    driverIndex = m_driverIndex;
    return true;
}

bool FloppyBridgeAPI::setDriverIndex(const int driverIndex)
{
    return false;
}

bool FloppyBridgeAPI::getBridgeMode(FloppyBridge::BridgeMode* mode) const
{
    return false;
}

bool FloppyBridgeAPI::setBridgeMode(const FloppyBridge::BridgeMode newMode) const
{
    return false;
}

bool FloppyBridgeAPI::getBridgeDensityMode(FloppyBridge::BridgeDensityMode* mode) const
{
    return false;
}

bool FloppyBridgeAPI::setBridgeDensityMode(const FloppyBridge::BridgeDensityMode newMode) const
{
    return false;
}

bool FloppyBridgeAPI::getAutoCacheMode(bool* autoCacheMode) const
{
    return false;
}

bool FloppyBridgeAPI::setAutoCacheMode(const bool autoCacheMode) const
{
    return false;
}

bool FloppyBridgeAPI::getComPort(TCharString* comPort) const
{
    return false;
}

bool FloppyBridgeAPI::setComPort(const TCHAR* comPort) const
{
    return false;
}

bool FloppyBridgeAPI::getComPortAutoDetect(bool* autoDetect) const
{
    return false;
}

bool FloppyBridgeAPI::setComPortAutoDetect(const bool autoDetect) const
{
    return false;
}

bool FloppyBridgeAPI::getDriveCableSelection(bool* connectToDriveB) const
{
    return false;
}

bool FloppyBridgeAPI::setDriveCableSelection(const bool connectToDriveB) const
{
    return false;
}

bool FloppyBridgeAPI::getDriveCableSelection(FloppyBridge::DriveSelection* connectToDrive) const
{
    return false;
}

bool FloppyBridgeAPI::setDriveCableSelection(const FloppyBridge::DriveSelection connectToDrive) const
{
    return false;
}

bool FloppyBridgeAPI::getSmartSpeedEnabled(bool* enabled) const
{
    return false;
}

bool FloppyBridgeAPI::setSmartSpeedEnabled(const bool enabled) const
{
    return false;
}

#endif /* __ANDROID__ */
