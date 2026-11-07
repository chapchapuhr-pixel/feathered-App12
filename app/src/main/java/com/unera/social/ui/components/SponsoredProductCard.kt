package com.unera.social.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.unera.social.model.*

/**
 * SponsoredProductCard - Counterpart to web SponsoredProductCard.tsx
 *
 * Web model mapping:
 * - product: Product model or custom object
 * - ad: AdCampaign or SponsoredFeedAd
 * - Computes sponsoredProductPost exactly matching web schema
 * - Renders complete Facebook-style Sponsored Product Card with:
 *   - Advertiser/Seller header, verified badge, and "Ad · Public" metadata
 *   - Product headline, description, location
 *   - Media photo/carousel with green price tag (and strikethrough discount price)
 *   - Facebook Blue "Shop Now" / custom CTA action bar
 *   - 7 3D animated reactions dock, comments, and share
 */
@Composable
fun SponsoredProductCard(
    product: Product? = null,
    ad: Any? = null, // Can be AdCampaign, SponsoredFeedAd, or null
    currentUser: User? = null,
    users: List<User> = emptyList(),
    onProfileClick: ((Long) -> Unit)? = null,
    onViewProduct: ((Long) -> Unit)? = null,
    onReact: ((Post, String) -> Unit)? = null,
    onOpenComments: ((Post) -> Unit)? = null,
    onShare: ((Post) -> Unit)? = null,
    onDelete: ((Long) -> Unit)? = null,
    onEdit: ((Long, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var isHidden by remember { mutableStateOf(false) }
    var isLiked by remember { mutableStateOf(false) }
    var showReactionDock by remember { mutableStateOf(false) }
    var currentReaction by remember { mutableStateOf<String?>(null) }
    var likesCount by remember { mutableStateOf(38) }

    val adCampaign = ad as? AdCampaign
    val feedAd = ad as? SponsoredFeedAd

    val prodId = remember(product, ad) {
        product?.id
            ?: adCampaign?.id
            ?: feedAd?.id
            ?: 101L
    }

    val rawImages = remember(product, ad) {
        val fromProd = product?.images?.filter { it.isNotBlank() }?.ifEmpty {
            listOfNotNull(product.imageUrl.takeIf { it.isNotBlank() })
        }
        if (!fromProd.isNullOrEmpty()) fromProd
        else {
            val fromCampaign = adCampaign?.mediaUrls?.filter { it.isNotBlank() }?.ifEmpty {
                listOfNotNull(adCampaign.mediaUrl?.takeIf { it.isNotBlank() })
            }
            if (!fromCampaign.isNullOrEmpty()) fromCampaign
            else {
                val fromFeedAd = listOfNotNull(feedAd?.imageUrl?.takeIf { it.isNotBlank() })
                if (fromFeedAd.isNotEmpty()) fromFeedAd
                else listOf("https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800")
            }
        }
    }

    val mainPrice = product?.price ?: 85000.0
    val discountPrice = product?.discountPrice
    val priceVal = discountPrice ?: mainPrice
    val currency = product?.currency ?: "TZS"

    val headline = feedAd?.headline?.takeIf { it.isNotBlank() }
        ?: adCampaign?.name?.takeIf { it.isNotBlank() }
        ?: product?.title ?: "Sponsored Product"
    val title = product?.title?.takeIf { it.isNotBlank() } ?: headline
    val description = product?.description?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: feedAd?.description?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: adCampaign?.description?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: ""

    val sellerId = product?.seller?.id
        ?: adCampaign?.id
        ?: feedAd?.brandId
        ?: 1L
    val sellerName = product?.seller?.name
        ?: adCampaign?.name?.takeIf { it.isNotBlank() }
        ?: feedAd?.sponsorName?.takeIf { it.isNotBlank() }
        ?: "Feathered Store"
    val sellerUsername = product?.seller?.username
        ?: feedAd?.sponsorName?.lowercase()?.replace(" ", "")
        ?: "seller"
    val sellerAvatar = product?.seller?.profileImageUrl?.takeIf { it.isNotBlank() }
        ?: feedAd?.sponsorAvatar?.takeIf { it.isNotBlank() }
        ?: adCampaign?.mediaUrl?.takeIf { it.isNotBlank() }
        ?: ""
    val isVerified = product?.seller?.isVerified ?: true

    val ctaText = feedAd?.ctaText?.takeIf { it.isNotBlank() }
        ?: adCampaign?.cta?.takeIf { it.isNotBlank() }
        ?: "Shop Now"
    val ctaUrl = feedAd?.ctaUrl
        ?: adCampaign?.link ?: ""
    val phoneNumber = product?.phoneNumber
        ?: adCampaign?.phone ?: ""
    val emailAddress = adCampaign?.email ?: ""
    val location = product?.location?.takeIf { it.isNotBlank() }
        ?: feedAd?.location?.takeIf { it.isNotBlank() }
        ?: adCampaign?.location ?: "Dar es Salaam, Tanzania"

    val author = remember(sellerId, sellerName, sellerUsername, sellerAvatar, isVerified) {
        product?.seller ?: User(
            id = sellerId,
            name = sellerName,
            username = sellerUsername,
            profileImageUrl = sellerAvatar,
            isVerified = isVerified
        )
    }

    val sponsoredMeta = remember(prodId, sellerId, headline, ctaText, ctaUrl, phoneNumber, emailAddress) {
        SponsoredMeta(
            adId = adCampaign?.id ?: feedAd?.id ?: prodId,
            advertiserId = sellerId,
            campaignName = adCampaign?.name ?: headline,
            headline = headline,
            ctaText = ctaText,
            ctaUrl = ctaUrl,
            contactType = if (phoneNumber.isNotBlank()) "phone" else "link",
            phoneNumber = phoneNumber,
            emailAddress = emailAddress
        )
    }

    val finalProduct = remember(product, prodId, title, mainPrice, discountPrice, currency, location, rawImages, description, author, phoneNumber) {
        product ?: Product(
            id = prodId,
            title = title,
            price = mainPrice,
            discountPrice = discountPrice,
            currency = currency,
            category = "Shopping",
            location = location,
            imageUrl = rawImages.firstOrNull() ?: "",
            images = rawImages,
            description = description,
            seller = author,
            phoneNumber = phoneNumber,
            isSponsored = true
        )
    }

    val sponsoredProductPost = remember(prodId, author, description, rawImages, headline, ctaText, ctaUrl, finalProduct, location, sponsoredMeta) {
        Post(
            id = prodId,
            author = author,
            content = description,
            mediaUrl = rawImages.firstOrNull(),
            mediaUrls = rawImages,
            timestamp = "Sponsored",
            likesCount = 38,
            commentsCount = 6,
            sharesCount = 3,
            isSponsored = true,
            postType = "product",
            sponsoredHeadline = headline,
            sponsoredCtaText = ctaText,
            sponsoredCtaUrl = ctaUrl,
            product = finalProduct,
            location = location,
            userId = author.id,
            sponsoredMeta = sponsoredMeta
        )
    }

    val handleAction: () -> Unit = {
        onViewProduct?.invoke(prodId)
        if (ctaUrl.isNotBlank()) {
            try {
                val fullUrl = if (ctaUrl.startsWith("http")) ctaUrl else "https://$ctaUrl"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
            } catch (_: Exception) {}
        } else if (phoneNumber.isNotBlank()) {
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")))
            } catch (_: Exception) {}
        }
    }

    if (isHidden) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sponsored product hidden.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
        }
        return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sponsored_product_card_${prodId}"),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. HEADER (Advertiser info + Verified Badge + "Ad · Public" metadata)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .clickable { onProfileClick?.invoke(author.id) }
                ) {
                    if (author.profileImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = author.profileImageUrl,
                            contentDescription = author.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1877F2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = author.name.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onProfileClick?.invoke(author.id) }
                    ) {
                        Text(
                            text = author.name,
                            color = Color(0xFFF8FAFC),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (author.isVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            VerifiedBadge(size = 14.dp)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "Ad",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF97316)
                        )
                        Text(text = "·", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Text(
                            text = "Sponsored",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                        Text(text = "·", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Public",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(12.dp)
                        )
                        if (location.isNotBlank()) {
                            Text(text = "·", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Text(
                                text = location,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 3-Dots Menu
                Box {
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Options",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingBag,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("View in MarketPoint", color = Color(0xFFF8FAFC), fontSize = 14.sp)
                                }
                            },
                            onClick = {
                                showMenu = false
                                onViewProduct?.invoke(prodId)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Hide ad", color = Color(0xFFF8FAFC), fontSize = 14.sp)
                                }
                            },
                            onClick = {
                                showMenu = false
                                isHidden = true
                            }
                        )
                    }
                }
            }

            // 2. HEADLINE & DESCRIPTION
            if (headline.isNotBlank()) {
                Text(
                    text = headline,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE4E6EB),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                )
            }

            if (description.isNotBlank()) {
                Text(
                    text = description,
                    fontSize = 15.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 21.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. PRODUCT MEDIA GRID (1, 2, 3, 4, 5+ images matching web Feeds.tsx)
            if (rawImages.isNotEmpty()) {
                MediaGrid(
                    images = rawImages,
                    onImageClick = { handleAction() }
                )
            }

            // 4. FACEBOOK SLIM BLUE CTA BAR ("Shop Now" / MarketPoint action)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B1120))
                    .border(BorderStroke(1.dp, Color(0xFF1E293B)))
                    .clickable { handleAction() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (ctaUrl.isNotBlank()) ctaUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/").firstOrNull()?.uppercase() ?: "MARKETPOINT STORE" else "MARKETPOINT STORE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF8FAFC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = handleAction,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1877F2),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = ctaText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // 5. REACTIONS BAR (Equipped with the 7 3D animated reactions!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 3D Animated Reactions Button
                FacebookReactionButton(
                    isLiked = isLiked,
                    myReaction = currentReaction,
                    likesCount = likesCount,
                    onLike = {
                        isLiked = !isLiked
                        currentReaction = if (isLiked) "like" else null
                        likesCount = if (isLiked) likesCount + 1 else (likesCount - 1).coerceAtLeast(0)
                        onReact?.invoke(sponsoredProductPost, if (isLiked) "like" else "none")
                    },
                    onReact = { reaction ->
                        isLiked = true
                        currentReaction = reaction
                        likesCount++
                        onReact?.invoke(sponsoredProductPost, reaction)
                    },
                    showReactionDock = showReactionDock,
                    onToggleReactionDock = { showReactionDock = it },
                    modifier = Modifier.weight(1f)
                )

                // Comments Action
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenComments?.invoke(sponsoredProductPost) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = Color(0xFFB0B3B8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Comment",
                        color = Color(0xFFB0B3B8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Share Action
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onShare?.invoke(sponsoredProductPost) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = Color(0xFFB0B3B8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share",
                        color = Color(0xFFB0B3B8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 6. 8dp border separator matching web feed styling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color(0xFF050B18))
            )
        }
    }
}
