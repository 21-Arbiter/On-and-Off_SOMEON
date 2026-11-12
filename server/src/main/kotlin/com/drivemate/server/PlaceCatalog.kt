package com.drivemate.server

import com.drivemate.core.CarPark
import com.drivemate.core.GeoPoint
import com.drivemate.core.MenuItem
import com.drivemate.core.Place

/**
 * Public places to meet around the demo area: made-up partner cafés and restaurants
 * (names, offers, menus, reviews and car park fees are examples; addresses are neighbourhood level).
 * A menu item suits the interests people list in their profiles.
 */
object PlaceCatalog {
    val places = listOf(
        Place(
            "cafe-route-pangyo", "카페 루트 판교", "카페", "판교 삼평동", GeoPoint(37.4010, 127.1085), "경기 성남시 분당구 삼평동", "cafe",
            parking = true, offer = "두 분 음료 10% 할인", rating = 4.5, reviews = 612,
            menu = listOf(
                MenuItem("루트 시그니처 라떼", 6_500, signature = true, mentions = 210, suits = listOf("커피")),
                MenuItem("바닐라빈 콜드브루", 6_000, mentions = 140, suits = listOf("커피", "드라이브", "러닝")),
                MenuItem("무화과 크림치즈 스콘", 4_800, mentions = 95, suits = listOf("베이킹", "브런치")),
                MenuItem("제철 과일 요거트 볼", 7_500, mentions = 60, suits = listOf("요가", "러닝", "산책")),
            ),
        ),
        Place(
            "baekhyeon-bookcafe", "백현 북카페", "카페", "판교 백현동", GeoPoint(37.3925, 127.1105), "경기 성남시 분당구 백현동", "cafe",
            parking = true, rating = 4.6, reviews = 430,
            menu = listOf(
                MenuItem("북카페 핸드드립", 6_000, signature = true, mentions = 180, suits = listOf("커피", "재즈", "전시")),
                MenuItem("얼그레이 파운드", 4_500, mentions = 120, suits = listOf("베이킹")),
                MenuItem("크로플 세트", 9_000, mentions = 85, suits = listOf("브런치", "맛집")),
                MenuItem("캐모마일 블렌딩 티", 5_500, mentions = 40, suits = listOf("요가", "산책")),
            ),
        ),
        Place(
            "drive-brew-jeongja", "드라이브 브루 정자", "카페", "분당 정자동", GeoPoint(37.3660, 127.1065), "경기 성남시 분당구 정자동", "cafe",
            parking = true, offer = "디저트 1개 무료", rating = 4.7, reviews = 980,
            menu = listOf(
                MenuItem("드라이브 콜드브루", 5_800, signature = true, mentions = 420, suits = listOf("커피", "드라이브")),
                MenuItem("흑임자 라떼", 6_300, mentions = 260, suits = listOf("커피", "맛집")),
                MenuItem("바스크 치즈케이크", 7_000, mentions = 190, suits = listOf("베이킹", "맛집")),
                MenuItem("그릭요거트 그래놀라", 6_800, mentions = 70, suits = listOf("러닝", "요가", "브런치")),
            ),
        ),
        Place(
            "tancheon-terrace", "탄천 테라스", "브런치", "분당 정자동", GeoPoint(37.3685, 127.1120), "경기 성남시 분당구 정자동", "food",
            carPark = CarPark("탄천 공영주차장", GeoPoint(37.3671, 127.1133), walkMin = 4, fee = "30분 600원 · 이후 10분 200원"),
            rating = 4.4, reviews = 520,
            menu = listOf(
                MenuItem("탄천 브런치 플레이트", 16_000, signature = true, mentions = 230, suits = listOf("브런치", "맛집")),
                MenuItem("리코타 팬케이크", 14_000, mentions = 150, suits = listOf("베이킹", "브런치", "사진")),
                MenuItem("연어 포케 볼", 15_000, mentions = 90, suits = listOf("요가", "러닝", "산책")),
                MenuItem("시즌 에이드", 6_500, mentions = 60, suits = listOf("사진", "여행")),
            ),
        ),
        Place(
            "seohyeon-sunset", "서현 노을 카페", "카페", "분당 서현동", GeoPoint(37.3845, 127.1240), "경기 성남시 분당구 서현동", "cafe",
            carPark = CarPark("서현역 공영주차장", GeoPoint(37.3853, 127.1227), walkMin = 3, fee = "30분 1,000원 · 이후 10분 500원"),
            rating = 4.5, reviews = 700,
            menu = listOf(
                MenuItem("노을 에이드", 6_800, signature = true, mentions = 260, suits = listOf("사진", "여행")),
                MenuItem("루프탑 아인슈페너", 6_500, mentions = 180, suits = listOf("커피", "재즈", "음악")),
                MenuItem("레몬 마들렌", 3_500, mentions = 110, suits = listOf("베이킹")),
                MenuItem("캠핑 스모어 쿠키", 4_000, mentions = 55, suits = listOf("캠핑")),
            ),
        ),
        Place(
            "migeum-lounge", "미금 라운지", "카페", "분당 구미동", GeoPoint(37.3505, 127.1090), "경기 성남시 분당구 구미동", "cafe",
            parking = true, rating = 4.3, reviews = 310,
            menu = listOf(
                MenuItem("라운지 플랫화이트", 5_800, signature = true, mentions = 120, suits = listOf("커피")),
                MenuItem("수제 샌드위치", 9_500, mentions = 90, suits = listOf("브런치", "캠핑", "여행")),
                MenuItem("말차 티라미수", 7_000, mentions = 70, suits = listOf("베이킹", "전시")),
            ),
        ),
        Place(
            "suji-highway-dining", "수지 하이웨이 다이닝", "식당", "수지 풍덕천동", GeoPoint(37.3230, 127.0960), "경기 용인시 수지구 풍덕천동", "food",
            parking = true, offer = "주차 2시간 무료", rating = 4.4, reviews = 860,
            menu = listOf(
                MenuItem("하이웨이 함박 스테이크", 15_000, signature = true, mentions = 300, suits = listOf("맛집", "드라이브")),
                MenuItem("트러플 크림 파스타", 16_000, mentions = 220, suits = listOf("맛집", "여행")),
                MenuItem("캠핑 바비큐 플래터", 32_000, mentions = 90, suits = listOf("캠핑", "야구")),
                MenuItem("리코타 샐러드", 12_000, mentions = 70, suits = listOf("요가", "러닝", "등산")),
            ),
        ),
        Place(
            "gwanggyo-lake-cafe", "광교 호수 카페", "카페", "광교 이의동", GeoPoint(37.2880, 127.0510), "경기 수원시 영통구 이의동", "cafe",
            parking = true, rating = 4.5, reviews = 1_120,
            menu = listOf(
                MenuItem("호수뷰 크림라떼", 6_800, signature = true, mentions = 380, suits = listOf("커피", "사진")),
                MenuItem("크루아상 샌드", 8_500, mentions = 210, suits = listOf("브런치", "여행", "산책")),
                MenuItem("딸기 생크림 케이크", 7_500, mentions = 150, suits = listOf("베이킹")),
            ),
        ),
        Place(
            "gwacheon-forest-cafe", "과천 숲 카페", "카페", "과천", GeoPoint(37.4300, 126.9900), "경기 과천시", "cafe",
            parking = true, rating = 4.6, reviews = 540,
            menu = listOf(
                MenuItem("숲속 핸드드립", 6_500, signature = true, mentions = 200, suits = listOf("커피", "등산", "산책")),
                MenuItem("쑥 라떼", 6_300, mentions = 130, suits = listOf("산책", "여행")),
                MenuItem("단호박 타르트", 5_500, mentions = 80, suits = listOf("베이킹")),
            ),
        ),
        Place(
            "yeoksam-rooftop", "역삼 루프탑", "식당", "강남 역삼동", GeoPoint(37.5005, 127.0370), "서울 강남구 역삼동", "food",
            carPark = CarPark("역삼 공영주차장", GeoPoint(37.4997, 127.0382), walkMin = 5, fee = "5분 400원"),
            rating = 4.3, reviews = 690,
            menu = listOf(
                MenuItem("루프탑 스테이크 플래터", 34_000, signature = true, mentions = 240, suits = listOf("맛집", "음악")),
                MenuItem("트러플 감자튀김", 11_000, mentions = 190, suits = listOf("야구", "맛집")),
                MenuItem("재즈 나이트 하이볼", 9_000, mentions = 150, suits = listOf("재즈", "음악")),
            ),
        ),
    )

    fun byId(id: String) = places.firstOrNull { it.id == id }
}
