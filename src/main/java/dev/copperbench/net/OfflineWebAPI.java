package dev.copperbench.net;

import net.mcreator.io.net.api.IWebAPI;
import net.mcreator.io.net.api.update.UpdateInfo;

import java.util.concurrent.CompletableFuture;

/** Stage 0 distribution API: no implicit network access or upstream release checks. */
public final class OfflineWebAPI implements IWebAPI {

	private final UpdateInfo updateInfo = UpdateInfo.empty();

	@Override public boolean initAPI() {
		return false;
	}

	@Override public void getWebsiteNews(CompletableFuture<String[]> data) {
		data.complete(null);
	}

	@Override public void getModOfTheWeekData(CompletableFuture<String[]> data) {
		data.complete(null);
	}

	@Override public String getSearchURL(String searchTerm) {
		return "https://mcreator.net/wiki";
	}

	@Override public UpdateInfo getUpdateInfo() {
		return updateInfo;
	}
}
