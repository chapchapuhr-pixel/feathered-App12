package com.unera.social.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.unera.social.R
import com.unera.social.model.User

/**
 * FeatheredTopBar - Matches web Header in Layout.tsx exactly.
 *
 * Left: Feathered Brand Button (Logo box + Bold Title)
 * Right:
 * 1. MarketPoint circular frosted button (with carrot orange badge)
 * 2. Search circular frosted button
 * 3. Notifications circular frosted button (with carrot orange badge)
 * 4. Profile Avatar button
 */
@Composable
fun FeatheredTopBar(
    currentUser: User,
    isGuest: Boolean = false,
    unreadNotificationsCount: Int = 0,
    marketplaceBadgeCount: Int = 0,
    isMarketplaceActive: Boolean = false,
    onHomeClick: () -> Unit = {},
    onMarketplaceClick: () -> Unit = {},
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onLoginClick: () -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    Surface(
        color = Color(0xFF0B1120),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .testTag("feathered_top_header")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LEFT: Brand Button (Name only, lowercase "feathered", app logo removed)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onHomeClick() }
                        .padding(start = 2.dp)
                        .testTag("app_brand_header")
                ) {
                    Text(
                        text = "feathered",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                }

                // RIGHT: MarketPoint, Search, Notifications, Profile Avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. MarketPoint Button: restored previous Storefront design, no shade border, layout shade color, sized to feathered text (24.dp)
                    Box {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isMarketplaceActive) Color(0xFF1E2D4A)
                                    else Color(0xFF131D33)
                                )
                                .clickable { onMarketplaceClick() }
                                .testTag("top_bar_marketplace_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = "MarketPoint",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        if (marketplaceBadgeCount > 0) {
                            val badgeStr = if (marketplaceBadgeCount > 99) "99+" else marketplaceBadgeCount.toString()
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .defaultMinSize(minWidth = 19.dp, minHeight = 19.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE41E3F))
                                    .border(1.5.dp, Color(0xFF0B1120), CircleShape)
                                    .padding(horizontal = if (badgeStr.length > 1) 4.5.dp else 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = badgeStr,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // 2. Search Button: no shade border, layout shade color, bold borders, sized to feathered text (24.dp)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131D33))
                            .clickable { onSearchClick() }
                            .testTag("top_bar_search_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search_bold),
                            contentDescription = "Search",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 3. Notification Button: no shade border, layout shade color, dark in middle + bold white borders, sized to feathered text (24.dp)
                    Box {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF131D33))
                                .clickable { onNotificationsClick() }
                                .testTag("top_bar_notifications_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bell),
                                contentDescription = "Notifications",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        if (unreadNotificationsCount > 0) {
                            val badgeStr = when {
                                unreadNotificationsCount > 99 -> "99+"
                                unreadNotificationsCount > 15 -> "15+"
                                unreadNotificationsCount > 9 -> "9+"
                                else -> unreadNotificationsCount.toString()
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .defaultMinSize(minWidth = 19.dp, minHeight = 19.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE41E3F))
                                    .border(1.5.dp, Color(0xFF0B1120), CircleShape)
                                    .padding(horizontal = if (badgeStr.length > 1) 4.5.dp else 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = badgeStr,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // 4. If guest: Small "Log In" button on top right
                    //    If logged in: User Profile Avatar button
                    if (isGuest || currentUser.id <= 0L) {
                        Button(
                            onClick = onLoginClick,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1877F2),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("top_bar_login_btn")
                        ) {
                            Text(
                                text = "Log In",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        // User Profile Avatar with Initials fallback: w-10 h-10 rounded-full
                        FeatheredAvatar(
                            name = currentUser.name,
                            username = currentUser.username,
                            imageUrl = currentUser.profileImageUrl,
                            size = 40.dp,
                            borderWidth = 2.dp,
                            borderColor = Color(0xFF334155).copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            onClick = onProfileClick,
                            modifier = Modifier.testTag("top_bar_profile_btn")
                        )
                    }
                }
            }

            // Bottom subtle border border-[#1E293B]
            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
        }
    }
}
