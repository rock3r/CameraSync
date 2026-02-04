package dev.sebastiano.camerasync.devices

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.camerasync.R
import dev.sebastiano.camerasync.domain.model.GpsLocation
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.Location
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationPuckColors
import org.maplibre.compose.location.LocationPuckSizes
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.material3.CompassButton
import org.maplibre.compose.material3.LocationPuckDefaults
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.spatialk.geojson.Position

@Composable
internal fun LocationCard(location: GpsLocation?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().height(200.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        if (location == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.acquiring_location),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val isDarkTheme = isSystemInDarkTheme()
            val mapStyle =
                if (isDarkTheme) "https://tiles.openfreemap.org/styles/dark"
                else "https://tiles.openfreemap.org/styles/positron"

            val cameraState =
                rememberCameraState(
                    CameraPosition(
                        target = Position(location.longitude, location.latitude),
                        zoom = 14.0,
                    )
                )

            // Update camera when location changes
            val locationFlow = remember { MutableStateFlow(location.toMapBoxLocation()) }

            val userState =
                rememberUserLocationState(
                    object : LocationProvider {
                        override val location: StateFlow<Location?> = locationFlow
                    }
                )
            LocationTrackingEffect(userState) {
                cameraState.animateTo(
                    CameraPosition(
                        target = Position(location.longitude, location.latitude),
                        zoom = cameraState.position.zoom, // Keep current zoom
                    )
                )
                locationFlow.value = location.toMapBoxLocation()
            }

            val styleState = rememberStyleState()
            Box(modifier = Modifier.fillMaxSize()) {
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    baseStyle = BaseStyle.Uri(mapStyle),
                    styleState = styleState,
                    cameraState = cameraState,
                    options =
                        MapOptions(
                            ornamentOptions =
                                OrnamentOptions(
                                    padding = PaddingValues(8.dp),
                                    isScaleBarEnabled = false,
                                    isLogoEnabled = false,
                                    isCompassEnabled = false,
                                )
                        ),
                ) {
                    LocationPuck(
                        idPrefix = "user-location",
                        cameraState = cameraState,
                        locationState = userState,
                        colors = LocationPuckDefaults.colors(),
                    )
                }

                Column(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    CompassButton(cameraState)
                    UserLocationButton {
                        cameraState.position =
                            CameraPosition(
                                target = Position(location.longitude, location.latitude),
                                zoom = cameraState.position.zoom, // Keep current zoom
                            )
                    }
                }
            }
        }
    }
}

private fun GpsLocation.toMapBoxLocation() =
    Location(
        Position(longitude, latitude),
        accuracy.toDouble(),
        bearing = null,
        bearingAccuracy = null,
        speed = null,
        speedAccuracy = null,
        timestamp.toTimeMark(),
    )

private fun ZonedDateTime.toTimeMark(): TimeMark {
    val now = ZonedDateTime.now()
    // Calculate the difference in milliseconds
    val diffMillis = toInstant().toEpochMilli() - now.toInstant().toEpochMilli()

    // Create a mark that is 'diff' away from the current monotonic 'now'
    return TimeSource.Monotonic.markNow() + diffMillis.milliseconds
}

@Composable
private fun UserLocationButton(
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
    puckColors: LocationPuckColors = LocationPuckDefaults.colors(),
    puckSizes: LocationPuckSizes = LocationPuckSizes(),
    contentDescription: String = stringResource(R.string.content_desc_user_location),
    size: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(size / 6),
    shape: Shape = CircleShape,
    onClick: () -> Unit,
) {
    ElevatedButton(
        modifier = modifier.requiredSize(size).aspectRatio(1f),
        onClick = onClick,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
    ) {
        Canvas(modifier = Modifier.semantics { this.contentDescription = contentDescription }) {
            drawCircle(
                puckColors.dotStrokeColor,
                puckSizes.dotRadius.toPx(),
                style = Stroke(puckSizes.dotStrokeWidth.toPx()),
            )
            drawCircle(puckColors.dotFillColorCurrentLocation, puckSizes.dotRadius.toPx())
        }
    }
}
