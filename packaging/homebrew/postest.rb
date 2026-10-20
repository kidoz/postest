cask "postest" do
  version "1.1.0"
  sha256 "706f2916eaeb7bb332a923df9cb7910875cd92a70ef3daec01e62846231ce24d"

  url "https://github.com/kidoz/postest/releases/download/v#{version}/Postest-#{version}.dmg"
  name "Postest"
  desc "Modern REST API client built with Kotlin and Compose"
  homepage "https://github.com/kidoz/postest"

  livecheck do
    url :url
    strategy :github_latest
  end

  depends_on macos: ">= :monterey"

  app "Postest.app"

  zap trash: [
    "~/.postest",
    "~/Library/Logs/Postest",
    "~/Library/Preferences/su.kidoz.postest.plist",
    "~/Library/Saved Application State/su.kidoz.postest.savedState",
  ]
end
