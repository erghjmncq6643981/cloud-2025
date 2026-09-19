package db

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestPublicModelsDoNotExposeCredentials(t *testing.T) {
	for _, model := range []interface{}{
		Gateway{Name: "test-trunk", Password: "test-only-secret"},
		FsExtension{Extension: "test-extension", Password: "test-only-secret"},
		ExtensionDetail{Extension: "test-extension", Password: "test-only-secret", XmlPath: "/private/config.xml"},
	} {
		data, err := json.Marshal(model)
		if err != nil {
			t.Fatal(err)
		}
		for _, forbidden := range []string{"password", "test-only-secret", "xml_path", "/private/"} {
			if strings.Contains(string(data), forbidden) {
				t.Fatalf("public model exposes %q", forbidden)
			}
		}
	}
}
