package org.homio.addon.esphome.service;

import org.homio.api.model.ActionResponseModel;

public interface CommunicatorService {

  void initialize();

  void destroy();

  default ActionResponseModel refresh() {
    return ActionResponseModel.showError("Not implemented");
  }
}
