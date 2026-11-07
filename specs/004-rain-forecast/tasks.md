# Tasks: Rain Forecast for Short City Names

Tasks are ordered test-first: add or update the tests before the implementation
they verify. The rain forecast shares the existing city forecast request,
authorization, cache, provider quota, and stale-data behavior.

## Specification and test fixtures

- [x] T001 Specify rain forecast eligibility and response behavior.
  - Define the canonical city-name Unicode-letter counting rule and strict
    seven-letter threshold.
  - Specify the conditional `rainForecast` response shape, provider data
    source, and the unchanged `/weather/cities` response.
  - References: `spec.md` Clarifications, Rules, and Acceptance criteria 1–7;
    `plan.md` Summary and Technical Context.

- [x] T002 Reuse shared representative city fixtures.
  - Use Berlin, Madrid, Ljubljana, Malmö, and Gdańsk from the pinned city
    catalogue; do not duplicate city record setup in tests.
  - Include eligible and ineligible names in the shared fixture coverage.
  - References: `spec.md` Acceptance criteria 3, 5, and 7; `plan.md`
    Design 2.

## Tests

- [x] T003 Test Unicode rain eligibility.
  - Cover accented precomposed letters, decomposed Unicode, punctuation,
    spaces, digits, and a name with exactly seven letters.
  - Verify fewer than seven letters is eligible and seven or more is not.
  - References: `spec.md` Rules 1–2 and Acceptance criterion 5;
    `plan.md` Test Strategy 1.

- [x] T004 Test provider rain-data mapping and validation.
  - Verify provider probabilities map to rain entries with the same three
    dates as the weather forecast.
  - Keep existing provider unit, range, and date validation covered.
  - Exercise city-specific provider data for the five shared test cities.
  - References: `spec.md` Rules 3 and Acceptance criterion 2;
    `plan.md` Test Strategy 2.

- [x] T005 Test forecast response serialization and service behavior.
  - Assert eligible cities return exactly three `rainForecast` entries and
    ineligible cities omit the property.
  - Assert `/weather/cities` exposes no rain-eligibility property for any
    catalogue entry.
  - Assert generic weather entries never expose
    `precipitationProbability`.
  - Verify cached and stale forecasts preserve conditional rain data.
  - Exercise the forecast API and service across the shared city fixture.
  - References: `spec.md` Rules 4–6 and Acceptance criteria 1, 3, 4, and 6;
    `plan.md` Test Strategy 3–5.

## Implementation and documentation

- [x] T006 Implement Unicode-letter eligibility and rain forecast model.
  - Count Unicode code points classified by `Character.isLetter` in the
    canonical city name.
  - Add the rain forecast day model with date and integer probability.
  - References: `spec.md` Rules 1–2; `plan.md` Design 3.

- [x] T007 Move precipitation probability out of generic weather entries.
  - Remove precipitation probability from `DailyForecast`.
  - Preserve the provider's daily probabilities separately and include them
    in the response only when the city is eligible.
  - Omit the null `rainForecast` property for ineligible cities.
  - Ensure stale responses retain eligible rain data.
  - References: `spec.md` Rules 3–6; `plan.md` Design 3.

- [x] T008 Update API contract and related feature documentation.
  - Update the weather service contract and city-forecast response example.
  - Document that the city catalogue remains unchanged and clients infer
    eligibility from the canonical city name.
  - Clarify the generic forecast-field migration in the city forecast spec.
  - References: `spec.md` Clarifications; `plan.md` Design 4.

## Verification

- [x] T009 Run Java 25 verification.
  - Run the focused weather verification and the full Maven reactor under
    Java 25.
  - Confirm tests do not call live external APIs and run `git diff --check`.
  - References: `plan.md` Design 5 and Test Strategy 6.
