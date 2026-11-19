package com.keepit.region.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepit.TestcontainersConfiguration;
import com.keepit.global.config.JpaConfig;
import com.keepit.region.entity.Region;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Flyway V3 거래 지역 시드가 정한 id·이름·순서대로 들어갔는지 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@ActiveProfiles("test")
class RegionSeedTest {

	@Autowired
	private RegionRepository regionRepository;

	private List<Region> seeded() {
		return regionRepository.findAll().stream()
				.filter(region -> region.getId() <= 67)
				.toList();
	}

	@Test
	@DisplayName("서울·인천·경기 67개가 모두 선택 가능 상태로 들어 있다")
	void 시드_67개_모두_오픈() {
		List<Region> regions = seeded();

		assertThat(regions).hasSize(67);
		assertThat(regions).allMatch(Region::isOpen);
	}

	@Test
	@DisplayName("id 범위는 서울 1~25, 인천 26~36, 경기 37~67이다")
	void 시도별_id_범위() {
		List<Region> regions = seeded();

		assertThat(regions).filteredOn(region -> region.getId() <= 25)
				.hasSize(25).allMatch(region -> region.getSido().equals("서울"));
		assertThat(regions).filteredOn(region -> region.getId() >= 26 && region.getId() <= 36)
				.hasSize(11).allMatch(region -> region.getSido().equals("인천"));
		assertThat(regions).filteredOn(region -> region.getId() >= 37)
				.hasSize(31).allMatch(region -> region.getSido().equals("경기"));
	}

	@Test
	@DisplayName("정해진 목록 순서대로 id가 붙는다 (구간 처음·끝과 개편 지역 확인)")
	void 목록_순서와_id() {
		assertRegion(1, "서울", "종로구");
		assertRegion(14, "서울", "마포구");
		assertRegion(25, "서울", "강동구");
		assertRegion(26, "인천", "제물포구");
		assertRegion(27, "인천", "영종구");
		assertRegion(33, "인천", "서해구");
		assertRegion(34, "인천", "검단구");
		assertRegion(36, "인천", "옹진군");
		assertRegion(37, "경기", "수원시");
		assertRegion(67, "경기", "양평군");
	}

	@Test
	@DisplayName("id를 직접 넣은 뒤에도 새 지역은 시드 범위 다음 id(68 이상)를 받는다")
	void 자동_증가_다음_값() {
		Region added = regionRepository.saveAndFlush(
				Region.builder().sido("테스트").sigungu("자동증가확인").open(false).build());

		assertThat(added.getId()).isGreaterThanOrEqualTo(68);
	}

	private void assertRegion(int id, String sido, String sigungu) {
		Region region = regionRepository.findById(id).orElseThrow();
		assertThat(region.getSido()).isEqualTo(sido);
		assertThat(region.getSigungu()).isEqualTo(sigungu);
	}

}
