package com.wnoicew.expensetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.ProfileManager
import com.wnoicew.expensetracker.data.UserProfile
import com.wnoicew.expensetracker.ui.components.HigInsetGroup
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerSheet(
    profileManager: ProfileManager,
    onDismiss: () -> Unit
) {
    val activeProfile by profileManager.activeProfile
    val profiles = profileManager.profiles

    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }

    var deletingProfileId by remember { mutableStateOf<String?>(null) }

    var newProfileName by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .safeDrawingPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Profiles",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Switch or manage your isolated memory accounts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Inset Group for Profiles
            HigInsetGroup {
                profiles.forEachIndexed { index, profile ->
                    val isActive = profile.id == activeProfile?.id
                    val isEditing = profile.id == editingProfileId
                    val isDeleting = profile.id == deletingProfileId

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        if (isEditing) {
                            // Inline Rename Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = renameText,
                                    onValueChange = { renameText = it },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        try {
                                            profileManager.renameProfile(profile.id, renameText)
                                            editingProfileId = null
                                            renameError = null
                                        } catch (e: Exception) {
                                            renameError = e.message
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                ) {
                                    Text("Save", fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = { editingProfileId = null },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Cancel", fontSize = 13.sp)
                                }
                            }
                            if (renameError != null) {
                                Text(renameError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        } else if (isDeleting) {
                            // Inline Delete Confirmation
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Erase all data for ${profile.name}?",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            profileManager.deleteProfile(profile.id)
                                            deletingProfileId = null
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Delete", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { deletingProfileId = null },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Cancel", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            // Normal Profile Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(profile.toBrush()),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(profile.initial, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = profile.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isActive) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = IncomeGreen.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "ACTIVE",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = IncomeGreen,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!isActive) {
                                        TextButton(
                                            onClick = {
                                                profileManager.setActiveProfile(profile.id)
                                                onDismiss()
                                            }
                                        ) {
                                            Text("Switch", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            editingProfileId = profile.id
                                            renameText = profile.name
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = { deletingProfileId = profile.id },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (index < profiles.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 10.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            // Quick Add Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = {
                        newProfileName = it
                        createError = null
                    },
                    placeholder = { Text("Add new profile...", fontSize = 14.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        try {
                            profileManager.createProfile(newProfileName)
                            newProfileName = ""
                        } catch (e: Exception) {
                            createError = e.message
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            if (createError != null) {
                Text(createError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
