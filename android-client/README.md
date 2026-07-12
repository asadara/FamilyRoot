# Family Tree Platform Android Client

Minimal Android client for creating persons in the Family Tree Platform backend.

## Setup

1. Open the project in Android Studio.
2. Ensure the backend is running on `http://localhost:3001` (or update `Config.BASE_URL` accordingly).

## Running

### On Android Emulator
- The `BASE_URL` is set to `http://10.0.2.2:3001` which is the emulator's loopback address to reach the host machine.
- Start the emulator.
- Run the app.

### On Physical Device
- Update `Config.BASE_URL` to your computer's LAN IP, e.g., `http://192.168.1.100:3001`.
- Ensure the device and computer are on the same network.
- Enable "USB debugging" and connect the device.
- Run the app.

## Features

- Single screen with "Create Person" button.
- On click, sends POST request to create a person with hardcoded data.
- Displays the response (personId and fullName) or error message.

## Architecture

- **Config**: Centralized configuration.
- **Models**: Data classes for request/response.
- **Network**: ApiService interface.
- **Repository**: Handles API calls with Retrofit.
- **ViewModel**: Manages UI state and business logic.
- **UI**: Compose-based screen.