package io.gnomon.tenancy.application.port.in;

import io.gnomon.tenancy.application.port.in.result.MembershipResult;
import java.util.List;
import java.util.UUID;

public interface ManageMembershipUseCase {

  List<MembershipResult> list(UUID actorUserId, String tenantSlug);

  MembershipResult add(AddMembershipCommand command);

  MembershipResult changeRole(ChangeMembershipRoleCommand command);

  void remove(RemoveMembershipCommand command);

  record AddMembershipCommand(UUID actorUserId, String tenantSlug, String userEmail, String role) {}

  record ChangeMembershipRoleCommand(
      UUID actorUserId, String tenantSlug, UUID membershipId, String role) {}

  record RemoveMembershipCommand(UUID actorUserId, String tenantSlug, UUID membershipId) {}
}
