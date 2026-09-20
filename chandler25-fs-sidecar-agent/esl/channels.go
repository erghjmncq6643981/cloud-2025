package esl

import (
	"encoding/json"
	"fmt"
)

// ChannelUUIDs queries authoritative channel identities without returning SIP or customer data.
func (c *Client) ChannelUUIDs() ([]string, error) {
	body, err := c.ExecuteAPI("show", "channels as json")
	if err != nil {
		return nil, err
	}
	return parseChannelUUIDs(body)
}

// parseChannelUUIDs rejects malformed or incomplete snapshots rather than reporting zero calls.
func parseChannelUUIDs(body string) ([]string, error) {
	var snapshot struct {
		RowCount *int `json:"row_count"`
		Rows     []struct {
			UUID string `json:"uuid"`
		} `json:"rows"`
	}
	if err := json.Unmarshal([]byte(body), &snapshot); err != nil {
		return nil, fmt.Errorf("invalid channel snapshot JSON: %w", err)
	}
	if snapshot.RowCount == nil || *snapshot.RowCount < 0 || *snapshot.RowCount != len(snapshot.Rows) {
		return nil, fmt.Errorf("incomplete channel snapshot")
	}
	result := make([]string, 0, len(snapshot.Rows))
	seen := make(map[string]bool, len(snapshot.Rows))
	for _, row := range snapshot.Rows {
		if row.UUID == "" || seen[row.UUID] {
			return nil, fmt.Errorf("invalid channel snapshot identity")
		}
		seen[row.UUID] = true
		result = append(result, row.UUID)
	}
	return result, nil
}
