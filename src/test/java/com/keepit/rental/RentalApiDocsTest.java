package com.keepit.rental;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keepit.TestcontainersConfiguration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제로 만들어지는 Swagger 문서에서 대여 API 스키마가 섞이지 않는지 확인한다.
 * springdoc은 클래스 이름이 같으면 스키마를 하나로 합치므로, 중첩 record 이름이 겹치면 다른 API 문서가 깨진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RentalApiDocsTest {

	@Autowired
	MockMvc mvc;

	@Test
	void 체크포인트_사진_스키마는_옷_상세_사진과_따로_보인다() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				// 옷 상세 Photo는 원래 필드 그대로
				.andExpect(jsonPath("$.components.schemas.Photo.properties.imageUrl").exists())
				.andExpect(jsonPath("$.components.schemas.Photo.properties.sortOrder").exists())
				// 체크포인트 등록·비교는 각자 이름
				.andExpect(jsonPath("$.components.schemas.CheckpointPhotoUploadItem.properties.imageId").exists())
				.andExpect(jsonPath("$.components.schemas.CheckpointPhoto.properties.imageUrl").exists())
				.andExpect(jsonPath("$.components.schemas.CheckpointStage.properties.step").exists());
	}

	@Test
	void 사진_등록_단계에는_RETURN_WASHED가_보이지_않는다() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(
						"$.paths['/api/v1/rentals/{rentalId}/checkpoints/{stage}/photos'].post.parameters[?(@.name=='stage')].schema.enum[*]",
						Matchers.containsInAnyOrder("HANDOVER", "RECEIVE", "RETURN")));
	}

}
