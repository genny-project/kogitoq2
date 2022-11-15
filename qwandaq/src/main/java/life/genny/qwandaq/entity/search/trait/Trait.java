package life.genny.qwandaq.entity.search.trait;

import java.util.Set;

import javax.json.bind.annotation.JsonbTypeAdapter;

import java.util.HashSet;

import io.quarkus.runtime.annotations.RegisterForReflection;
import life.genny.qwandaq.datatype.capability.core.Capability;
import life.genny.qwandaq.datatype.capability.core.node.CapabilityNode;
import life.genny.qwandaq.serialization.adapters.CapabilityRequirementAdapter;

/**
 * Trait
 */
@RegisterForReflection
public abstract class Trait {

	private String code;
	private String name;

	// @JsonbTransient
	@JsonbTypeAdapter(CapabilityRequirementAdapter.class)
	private Set<CapabilityRequirement> capabilityRequirements = new HashSet<>();

	public Trait() {
	}

	public Trait(String code, String name) {
		this.code = code;
		this.name = name;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Set<CapabilityRequirement> getCapabilityRequirements() {
		return capabilityRequirements;
	}

	public void setCapabilityRequirements(Set<CapabilityRequirement>  capabilities) {
		this.capabilityRequirements = capabilities;
	}

	public Trait addCapabilityRequirement(CapabilityRequirement capability) {
		this.capabilityRequirements.add(capability);
		return this;
	}

	public Trait addCapabilityRequirement(Capability capability, boolean requiresAll) {
		return addCapabilityRequirement(CapabilityRequirement.fromCapability(capability, requiresAll));
	}

	public Trait addCapabilityRequirement(String code, boolean requiresAll, CapabilityNode... nodes) {
		CapabilityRequirement req = new CapabilityRequirement(code, requiresAll, nodes);
		return addCapabilityRequirement(req);
	}

}
