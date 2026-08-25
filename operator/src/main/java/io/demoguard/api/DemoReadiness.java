package io.demoguard.api;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("demoguard.dev")
@Version("v1alpha1")
@Kind("DemoReadiness")
@Plural("demoreadinesses")
public class DemoReadiness extends CustomResource<Void, DemoReadinessStatus> implements Namespaced {
}
