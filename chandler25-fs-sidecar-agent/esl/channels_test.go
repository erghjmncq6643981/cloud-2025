package esl

import "testing"

// TestChannelSnapshotValidation distinguishes empty state from failed or truncated queries.
func TestChannelSnapshotValidation(t *testing.T) {
	for _, body := range []string{`-ERR unavailable`, `{}`, `null`, `{"row_count":1}`, `{"row_count":1,"rows":[{}]}`, `{"row_count":2,"rows":[{"uuid":"a"},{"uuid":"a"}]}`} {
		if _, err := parseChannelUUIDs(body); err == nil {
			t.Fatalf("accepted invalid snapshot %s", body)
		}
	}
	for _, body := range []string{`{"row_count":0}`, `{"row_count":1,"rows":[{"uuid":"a"}]}`} {
		if _, err := parseChannelUUIDs(body); err != nil {
			t.Fatal(err)
		}
	}
}
