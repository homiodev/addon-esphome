package org.homio.addon.esphome.api.discovery;

import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.service.discovery.ItemDiscoverySupport;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import javax.jmdns.ServiceInfo;
import java.util.List;

@Log4j2
@Component
public class ESPHomeDiscoveryParticipant implements ItemDiscoverySupport {

  // public static final String PROPERTY_HOSTNAME = "hostname";

  @Override
  public @NotNull String getName() {
    return "scan-esphome-devices";
  }

  @Override
  public @Nullable DeviceScannerResult scan(@NotNull Context context, @NotNull ProgressBar progressBar) {
    List<ServiceInfo> services = context.network().scanMDNS("_esphomelib._tcp.local.");
    for (ServiceInfo service : services) {
      if ("esphomelib".equals(service.getApplication())) {
        String serviceName = service.getName();
        log.info("Found ESPHome devices via mDNS:{} v4:{} v6:{}", serviceName, service.getInet4Addresses(), service.getInet6Addresses());
        //return new ThingUID(BindingConstants.THING_TYPE_DEVICE, "REMOVEMEWHENADDING" + serviceName);
        // final ThingUID deviceUID = getThingUID(service);
        //  if (deviceUID != null) {


               /* StringBuilder b = new StringBuilder("ESPHome device ");
                if (service.getName() != null) {
                    b.append(service.getName());
                }
                if (service.getServer() != null) {
                    b.append(" (").append(service.getServer()).append(")");
                }
                if (service.getInet4Addresses().length != 0) {
                    b.append(" ").append(Arrays.stream(service.getInet4Addresses()).map(e -> e.getHostAddress())
                                               .collect(Collectors.joining()));
                }

                return DiscoveryResultBuilder.create(deviceUID).withThingType(BindingConstants.THING_TYPE_DEVICE)
                                             .withProperty(PROPERTY_HOSTNAME, service.getServer()).withLabel(b.toString())
                                             .withRepresentationProperty(PROPERTY_HOSTNAME).build();*/
        // }
      }
    }

    return null;
  }
}
