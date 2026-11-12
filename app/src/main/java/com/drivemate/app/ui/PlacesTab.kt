package com.drivemate.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.app.R
import com.drivemate.core.CarPark
import com.drivemate.core.Conversation
import com.drivemate.core.MenuItem
import com.drivemate.core.MenuPicks
import com.drivemate.core.Place
import com.drivemate.core.PlaceSuggestion
import com.drivemate.core.PlaceSuggestions
import com.drivemate.core.Profile
import com.drivemate.core.km
import com.drivemate.core.won
import java.util.Locale

/**
 * Where to meet a match: places on both drives home (how far each would go out of
 * their way), a proposal either side can accept, and once agreed, the route.
 */
@Composable
fun PlacesTab(social: Social, chatWith: String?, onChatWith: (String) -> Unit) {
    val matches = social.inbox.matches
    val open = matches.firstOrNull { it.userId == chatWith } ?: matches.firstOrNull()
    if (open == null) {
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                PanelTitle("만남 장소", Icons.Outlined.LocationOn)
                Muted("매칭이 성사되면 두 분의 퇴근길에 있는 공개된 장소를 추천하고, 정해지면 바로 길안내를 시작해 드려요.")
                Image(
                    painterResource(R.drawable.place_cafe),
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(22.dp)),
                )
            }
        }
        return
    }
    var suggestions by remember(open.userId) { mutableStateOf<PlaceSuggestions?>(null) }
    var failed by remember(open.userId) { mutableStateOf(false) }
    LaunchedEffect(open.userId) {
        val loaded = social.places(open.userId)
        suggestions = loaded
        failed = loaded == null
    }
    // The place opened on a big map, by its id.
    var detail by remember(open.userId) { mutableStateOf<String?>(null) }
    // With someone else in the car, whom the places are for stays underneath, out of focus, like the conversations.
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(28.dp).then(if (social.covered) Modifier.hushed(DiscreetBlur) else Modifier), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Outlined.LocationOn, null, tint = palette.pink, modifier = Modifier.size(38.dp))
                Text("만남 장소", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                matches.forEach { PersonChip(it, selected = it.userId == open.userId) { onChatWith(it.userId) } }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Panel(Modifier.weight(1.3f).fillMaxHeight(), spacing = 18.dp) {
                    PanelTitle("${open.profile.name}님과 함께 가기 좋은 곳")
                    suggestions?.hint?.let { hint ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, null, tint = palette.blue, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(hint, fontSize = 21.sp, color = palette.blue, fontWeight = FontWeight.Bold)
                        }
                    }
                    val loaded = suggestions
                    when {
                        failed -> Muted("장소를 불러오지 못했어요. 잠시 후 다시 열어 주세요.")
                        loaded == null -> Muted("두 분의 퇴근길을 살펴보고 있어요…")
                        loaded.places.isEmpty() -> Muted("두 분의 동선에 맞는 장소가 아직 없어요.")
                        else -> loaded.places.forEach {
                            PlaceRow(it, open, social.myId, onOpen = { detail = it.place.id }) { social.propose(open.userId, it.place.id) }
                        }
                    }
                }
                val agreed = suggestions?.places?.firstOrNull { it.place.id == open.meetup?.place?.id }
                MeetupPanel(open, social, suggestions?.hint, agreed, Modifier.weight(1f).fillMaxHeight(), onOpenMap = { detail = it })
            }
        }
        if (social.covered) {
            DiscreetCover(social.reveal, Modifier.matchParentSize().padding(28.dp).clip(RoundedCornerShape(28.dp)))
        } else {
            suggestions?.places?.firstOrNull { it.place.id == detail }?.let { suggestion ->
                PlaceSheet(suggestion, open, social, suggestions?.hint, onClose = { detail = null })
            }
        }
    }
}

@Composable
private fun PersonChip(conversation: Conversation, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.pinkSoft else palette.card)
            .border(1.dp, if (selected) palette.pink.copy(alpha = 0.5f) else palette.cardBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonPhoto(conversation.profile, 44.dp)
        Spacer(Modifier.width(10.dp))
        Text(conversation.profile.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (selected) palette.onPinkSoft else palette.text)
    }
}

/** One place to meet; pressed anywhere but its button, it opens on a big map ([onOpen]). */
@Composable
private fun PlaceRow(suggestion: PlaceSuggestion, with: Conversation, myId: String, onOpen: () -> Unit, onPropose: () -> Unit) {
    val place = suggestion.place
    val meetup = with.meetup?.takeIf { it.place.id == place.id }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.cardSoft)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceMap(place, Modifier.width(170.dp).height(124.dp), aroundM = 350.0, onOpen = onOpen)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(place.name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text("${place.category} · ${place.area}", fontSize = 19.sp, color = palette.textMuted)
            }
            Text(onTheWay(suggestion.myDetourM, suggestion.theirDetourM, with.profile.name), fontSize = 20.sp, color = palette.blue, fontWeight = FontWeight.Bold)
            ReasonChips(suggestion.reasons.drop(1))
        }
        Spacer(Modifier.width(16.dp))
        when {
            meetup?.accepted == true -> Pill("약속 장소", background = palette.green)
            meetup != null && meetup.proposedBy == myId -> Pill("제안함", background = palette.neutralButton, color = palette.text)
            else -> Text(
                "제안하기",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.pink)
                    .clickable(onClick = onPropose)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun RouteNote(icon: ImageVector, text: String, color: Color) {
    Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
    Spacer(Modifier.width(10.dp))
    Text(text, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun MeetupFact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = palette.blue, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 20.sp, color = palette.blue, fontWeight = FontWeight.Bold)
    }
}

/** How far out of their way a place takes each of the two (on the way: within 150 m). */
private fun onTheWay(mine: Int, theirs: Int, name: String) = when {
    mine < 150 && theirs < 150 -> "두 분 모두 퇴근길에 바로 있어요"
    mine < 150 -> "내 퇴근길에 바로 있어요 · ${name}님은 ${km(theirs)} 더 가요"
    theirs < 150 -> "${name}님 퇴근길에 바로 있어요 · 나는 ${km(mine)} 더 가요"
    else -> "들르려면 나 ${km(mine)}, ${name}님 ${km(theirs)} 더 가요"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonChips(reasons: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        reasons.forEach { Pill(it, background = palette.card, color = palette.textMuted, size = 17.sp) }
    }
}

/**
 * Where [place] is: its street map [aroundM] metres about it, with a pin (a public place,
 * so it can be exact). Until the tiles arrive the map's background colour stands in.
 * With [onOpen], a corner sign says it opens bigger.
 */
@Composable
private fun PlaceMap(place: Place, modifier: Modifier, aroundM: Double, maxZoom: Int = 17, onOpen: (() -> Unit)? = null, carPark: CarPark? = null) {
    val p = palette
    val text = rememberTextMeasurer()
    BoxWithConstraints(modifier.clip(RoundedCornerShape(16.dp)).background(p.mapBackground).then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)) {
        val density = LocalDensity.current
        val view = remember(place.id, aroundM, carPark, constraints.maxWidth, constraints.maxHeight) {
            val c = place.point
            val margin = aroundM / 3
            val corners = listOf(c.moved(aroundM, aroundM), c.moved(-aroundM, -aroundM)) +
                listOfNotNull(carPark?.point?.moved(margin, margin), carPark?.point?.moved(-margin, -margin))
            MapView.fit(corners, constraints.maxWidth, constraints.maxHeight, 0f, 0f, with(density) { 256.dp.toPx() }, maxZoom = maxZoom)
        }
        val tiles = rememberMapTiles(view)
        Canvas(Modifier.fillMaxSize()) {
            view.tiles.forEach { tile ->
                val image = tiles[tile] ?: return@forEach
                val (offset, tileSize) = view.place(tile)
                drawImage(image, dstOffset = offset, dstSize = tileSize, colorFilter = p.mapFilter, filterQuality = FilterQuality.Medium)
            }
            // A pin: its point on the place, a white dot in the head.
            val at = view.at(place.point)
            val r = 13.dp.toPx()
            val head = at - Offset(0f, r * 1.9f)
            val pin = Path().apply {
                moveTo(at.x, at.y)
                lineTo(head.x - r * 0.78f, head.y + r * 0.62f)
                lineTo(head.x + r * 0.78f, head.y + r * 0.62f)
                close()
            }
            drawCircle(Color.Black.copy(alpha = 0.18f), r * 0.55f, at, style = Stroke(2.dp.toPx()))
            drawPath(pin, p.pink)
            drawCircle(p.pink, r, head)
            drawCircle(Color.White, r * 0.42f, head)
            // The public car park to use instead, a P sign like the roads'.
            carPark?.let {
                val spot = view.at(it.point)
                val side = 30.dp.toPx()
                drawRoundRect(p.blue, spot - Offset(side / 2, side / 2), Size(side, side), CornerRadius(6.dp.toPx()))
                val letter = text.measure("P", TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White))
                drawText(letter, topLeft = spot - Offset(letter.size.width / 2f, letter.size.height / 2f))
            }
            // OpenStreetMap's licence: credit it on every map.
            val credit = text.measure("© OpenStreetMap", TextStyle(fontSize = 10.sp, color = p.textMuted))
            drawRect(p.card.copy(alpha = 0.75f), Offset(size.width - credit.size.width - 8.dp.toPx(), size.height - credit.size.height - 2.dp.toPx()), Size(credit.size.width + 8.dp.toPx(), credit.size.height + 2.dp.toPx()))
            drawText(credit, topLeft = Offset(size.width - credit.size.width - 4.dp.toPx(), size.height - credit.size.height - 1.dp.toPx()))
        }
        if (onOpen != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).background(p.card.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ZoomIn, null, tint = p.text, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * One place, big, over the tab: its streets close up (in and out with the buttons), what it is, when
 * both pass by and how far out of the way it takes each, and the proposal.
 */
@Composable
private fun PlaceSheet(suggestion: PlaceSuggestion, with: Conversation, social: Social, hint: String?, onClose: () -> Unit) {
    val place = suggestion.place
    val meetup = with.meetup?.takeIf { it.place.id == place.id }
    var around by remember(place.id) { mutableStateOf(DETAIL_AROUND_M) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Panel(
            Modifier
                .fillMaxSize()
                .padding(40.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Box(Modifier.weight(1.5f).fillMaxHeight()) {
                    PlaceMap(place, Modifier.fillMaxSize(), aroundM = around, maxZoom = 18, carPark = place.carPark.takeUnless { place.parking })
                    Column(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ZoomButton(Icons.Filled.Add, enabled = around > DETAIL_AROUND_M / 4) { around /= 2 }
                        ZoomButton(Icons.Filled.Remove, enabled = around < DETAIL_AROUND_M * 8) { around *= 2 }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(place.name, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Muted("${place.category} · ${place.address}", 21.sp)
                        place.rating?.let { Rating(it, place.reviews) }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            hint?.let { MeetupFact(Icons.Outlined.Schedule, it) }
                            MeetupFact(Icons.Outlined.Route, onTheWay(suggestion.myDetourM, suggestion.theirDetourM, with.profile.name))
                            ParkingFacts(place)
                        }
                        place.offer?.let { Pill("제휴 혜택 · $it", background = palette.pinkSoft, color = palette.onPinkSoft, size = 19.sp) }
                        MenuSection(place, with.profile, social.me)
                        SafetyTip()
                        Muted("메뉴·리뷰·주차 요금은 시연용 예시예요", 16.sp)
                    }
                    when {
                        meetup?.accepted == true -> Pill("${with.profile.name}님과 약속한 장소", background = palette.green)
                        meetup != null && meetup.proposedBy == social.myId -> Pill("${with.profile.name}님께 제안했어요", background = palette.neutralButton, color = palette.text)
                        else -> SheetAction("이곳으로 제안하기", palette.pink, Color.White) {
                            social.propose(with.userId, place.id)
                            onClose()
                        }
                    }
                    SheetAction("닫기", palette.neutralButton, palette.textMuted, onClose)
                }
            }
        }
    }
}

@Composable
private fun ZoomButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .shadow(3.dp, CircleShape)
            .background(palette.card, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) palette.text else palette.textMuted.copy(alpha = 0.4f), modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun SheetAction(text: String, background: Color, color: Color, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
    )
}

/** ★ 4.7 · 리뷰 980개 */
@Composable
private fun Rating(rating: Double, reviews: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, tint = Color(0xFFFFB400), modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(6.dp))
        Text("$rating", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text("리뷰 ${"%,d".format(Locale.KOREA, reviews)}개", fontSize = 19.sp, color = palette.textMuted)
    }
}

/** Parking there; or, where there is none, the public car park near it, its walk and its fee (the route goes there, see [driveTo]). */
@Composable
private fun ParkingFacts(place: Place) {
    val carPark = place.carPark
    when {
        place.parking -> MeetupFact(Icons.Outlined.LocalParking, "주차할 수 있어요")
        carPark != null -> {
            MeetupFact(Icons.Outlined.LocalParking, "주차는 근처 ${carPark.name} · 걸어서 ${carPark.walkMin}분")
            MeetupFact(Icons.Outlined.Payments, "주차 요금 ${carPark.fee}")
        }
    }
}

/** The house's signature and what reviews pick, then one for each of the two from their interests. */
@Composable
private fun MenuSection(place: Place, them: Profile, me: Profile?) {
    if (place.menu.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("추천 메뉴", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        MenuPicks.signature(place.menu)?.let { MenuLine("시그니처", it, "이 집이 가장 자신 있는 메뉴") }
        MenuPicks.mostMentioned(place.menu)?.let { MenuLine("리뷰 추천", it, "리뷰 ${"%,d".format(Locale.KOREA, it.mentions)}개에서 찾았어요") }
    }
    val (forThem, forMe) = MenuPicks.forTwo(place.menu, them.interests, me?.interests.orEmpty())
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("취향 맞춤", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        forThem?.let { MenuLine("${them.name}님께 권하기", it.item, it.interest?.let { i -> "${them.name}님이 좋아하는 ‘$i’${wa(i)} 어울려요" } ?: "이 집 시그니처예요") }
        if (me != null) forMe?.let { MenuLine("나에게", it.item, it.interest?.let { i -> "내가 좋아하는 ‘$i’${wa(i)} 어울려요" } ?: "이 집 시그니처예요") }
    }
}

@Composable
private fun MenuLine(label: String, item: MenuItem, why: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.onPinkSoft, modifier = Modifier.width(170.dp).padding(top = 3.dp))
        Column {
            Text("${item.name} · ${won(item.price)}", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(why, fontSize = 17.sp, color = palette.textMuted)
        }
    }
}

/** 과 after a final consonant, 와 after a vowel (e.g. 맛집과, 커피와). */
private fun wa(word: String): String {
    val last = word.lastOrNull() ?: return "와"
    return if (last in '가'..'힣' && (last - '가') % 28 != 0) "과" else "와"
}

/** Where to drive to: the place, or its public car park when it has no parking of its own. */
private fun Place.driveTo(): Place = carPark?.takeIf { !parking }?.let { copy(id = "$id-parking", name = it.name, category = "공영주차장", point = it.point) } ?: this

/** How much of the streets round a place the big map shows at first, each way. */
private const val DETAIL_AROUND_M = 300.0

/**
 * Where things stand with the match: nothing yet, a proposal either way, or the agreed place
 * and its route; with when both are on the road ([hint]) and how far it takes each ([suggestion]).
 */
@Composable
private fun MeetupPanel(with: Conversation, social: Social, hint: String?, suggestion: PlaceSuggestion?, modifier: Modifier, onOpenMap: (placeId: String) -> Unit) {
    val name = with.profile.name
    val meetup = with.meetup
    var routeSent by remember(meetup?.place?.id) { mutableStateOf<Boolean?>(null) }
    Panel(modifier) {
        PanelTitle("약속")
        SafetyTip()
        if (meetup == null) {
            Muted("마음에 드는 곳을 골라 ${name}님께 제안해 보세요. 수락하면 바로 길안내를 시작할 수 있어요.")
            return@Panel
        }
        PlaceMap(meetup.place, Modifier.fillMaxWidth().height(230.dp), aroundM = 700.0, onOpen = { onOpenMap(meetup.place.id) }, carPark = meetup.place.carPark.takeUnless { meetup.place.parking })
        Text(meetup.place.name, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Muted("${meetup.place.category} · ${meetup.place.address}", 20.sp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            hint?.let { MeetupFact(Icons.Outlined.Schedule, it) }
            suggestion?.let { MeetupFact(Icons.Outlined.Route, onTheWay(it.myDetourM, it.theirDetourM, name)) }
            ParkingFacts(meetup.place)
            MenuPicks.forInterests(meetup.place.menu, with.profile.interests)?.let { MeetupFact(Icons.Outlined.Restaurant, "${name}님께 권할 메뉴 · ${it.item.name}") }
        }
        meetup.place.offer?.let { Pill("제휴 혜택 · $it", background = palette.pinkSoft, color = palette.onPinkSoft, size = 19.sp) }
        when {
            meetup.accepted -> {
                Pill("${name}님과 약속 확정", background = palette.green)
                Spacer(Modifier.weight(1f))
                // what came of pressing it, in a line kept for it: the button below never moves
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    when (routeSent) {
                        true -> RouteNote(Icons.Outlined.CheckCircle, meetup.place.driveTo().takeIf { it.id != meetup.place.id }?.let { "${it.name}까지 길안내를 보냈어요" } ?: "내비게이션에 목적지를 보냈어요", palette.green)
                        false -> RouteNote(Icons.Outlined.ErrorOutline, "내비게이션에 연결하지 못했어요. 다시 눌러 주세요", palette.pink)
                        null -> Unit
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(44.dp))
                        .background(palette.pink)
                        .clickable { routeSent = social.navigate(meetup.place.driveTo()) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Navigation, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("길안내 시작", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            meetup.proposedBy == social.myId -> Muted("${name}님께 제안했어요. 답을 기다리고 있어요.")
            else -> {
                Text("${name}님이 이곳에서 만나자고 했어요", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "수락하기",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(44.dp))
                        .background(palette.pink)
                        .clickable { social.accept(with.userId) }
                        .padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
