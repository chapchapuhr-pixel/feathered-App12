package com.unera.social.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unera.social.R
import com.unera.social.ui.screens.formatCount

/**
 * DiscussButton
 *
 * Exact Feathered web implementation:
 * <button
 *   type="button"
 *   className="flex items-center gap-1.5 text-[#F8FAFC] hover:text-[#38BDF8] transition-colors focus:outline-none p-1 rounded-lg hover:bg-[#1E293B]/60"
 *   onClick={(e) => {
 *     e.preventDefault();
 *     e.stopPropagation();
 *     handleOpenComments(e);
 *   }}
 *   aria-label="Discuss & Comments"
 *   title="Discuss"
 * >
 *   <i className="far fa-comment text-[22px]"></i>
 *   {commentCount > 0 && (
 *     <span className="text-[14px] font-semibold text-[#F8FAFC]">
 *       {formatCount(commentCount)}
 *     </span>
 *   )}
 * </button>
 */
@Composable
fun DiscussButton(
    commentCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "discuss_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Web transition-colors: text-[#F8FAFC] hover:text-[#38BDF8]
    val contentColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF38BDF8) else Color(0xFFF8FAFC),
        animationSpec = tween(durationMillis = 150),
        label = "discuss_content_color"
    )

    // Web hover:bg-[#1E293B]/60 p-1 rounded-lg
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF1E293B).copy(alpha = 0.60f) else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "discuss_bg_color"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp) // gap-1.5 = 6dp
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_far_comment),
            contentDescription = "Discuss & Comments",
            tint = contentColor,
            modifier = Modifier.size(22.dp) // text-[22px]
        )

        if (commentCount > 0) {
            Text(
                text = formatCount(commentCount),
                color = contentColor,
                fontSize = 14.sp, // text-[14px]
                fontWeight = FontWeight.SemiBold // font-semibold
            )
        }
    }
}
