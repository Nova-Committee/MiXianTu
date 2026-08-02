package com.iafenvoy.mxt.runtime.sect;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.SectData;
import com.iafenvoy.mxt.attachment.SectTerritoryData;
import com.iafenvoy.mxt.data.sect.SectDefinition;
import com.iafenvoy.mxt.data.sect.SectDefinition.Exchange;
import com.iafenvoy.mxt.data.sect.SectDefinition.Rank;
import com.iafenvoy.mxt.data.sect.SectDefinition.Task;
import com.iafenvoy.mxt.event.SectEvent.JoinPost;
import com.iafenvoy.mxt.event.SectEvent.JoinPre;
import com.iafenvoy.mxt.event.SectEvent.LeavePost;
import com.iafenvoy.mxt.event.SectEvent.LeavePre;
import com.iafenvoy.mxt.event.SectEvent.PromotePost;
import com.iafenvoy.mxt.event.SectEvent.PromotePre;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Comparator;
import java.util.List;

/**
 * Membership and contribution transactions for data-driven sects.
 */
public final class SectService {
    private SectService() {
    }

    public static Result join(SectData data, Identifier id, SectDefinition definition) {
        if (definition.ranks().isEmpty()) return Result.rejected(Failure.DISABLED);
        if (data.member()) return Result.rejected(Failure.ALREADY_MEMBER);
        if (NeoForge.EVENT_BUS.post(new JoinPre(data, id)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        data.join(id, definition.ranks().getFirst().id());
        NeoForge.EVENT_BUS.post(new JoinPost(data, id));
        return Result.changedResult();
    }

    public static Result leave(SectData data) {
        if (!data.member()) return Result.rejected(Failure.NOT_MEMBER);
        Identifier id = data.sect().orElseThrow();
        if (NeoForge.EVENT_BUS.post(new LeavePre(data, id)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        data.leave();
        NeoForge.EVENT_BUS.post(new LeavePost(data, id));
        return Result.changedResult();
    }

    public static Result addContribution(SectData data, Identifier id, SectDefinition definition, int amount) {
        if (data.sect().filter(id::equals).isEmpty()) return Result.rejected(Failure.NOT_MEMBER);
        if (amount <= 0) return Result.rejected(Failure.INVALID_CONTRIBUTION);
        data.addContribution(amount);
        return Result.changedResult();
    }

    /**
     * Promotes to the next strictly higher configured rank after atomically paying its declared costs.
     */
    public static Result promote(SectData data, Identifier id, SectDefinition definition, ResourceHolderData resources, FormulaContext context) {
        if (data.sect().filter(id::equals).isEmpty()) return Result.rejected(Failure.NOT_MEMBER);
        Rank current = definition.ranks().stream().filter(rank -> rank.id().equals(data.rank())).findFirst().orElse(null);
        if (current == null) return Result.rejected(Failure.INVALID_RANK);
        Rank next = definition.ranks().stream().filter(rank -> rank.priority() > current.priority() && rank.minContribution() <= data.contribution())
                .min(Comparator.comparingInt(Rank::priority)).orElse(null);
        if (next == null) return Result.rejected(Failure.NO_PROMOTION);
        Evaluation costs;
        try {
            costs = ResourceTransactions.evaluate(next.promotionCosts(), context);
        } catch (IllegalArgumentException exception) {
            return Result.rejected(Failure.INVALID_COST);
        }
        if (NeoForge.EVENT_BUS.post(new PromotePre(data, id)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        ResourceTransactions.Result payment = ResourceTransactions.tryConsume(resources, costs);
        if (!payment.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE);
        data.setRank(next.id());
        NeoForge.EVENT_BUS.post(new PromotePost(data, id));
        return Result.changedResult();
    }

    public static boolean hasPermission(SectData data, Identifier id, SectDefinition definition, Identifier permission) {
        if (data.sect().filter(id::equals).isEmpty()) return false;
        return definition.ranks().stream().filter(rank -> rank.id().equals(data.rank())).findFirst()
                .map(rank -> rank.permissions().contains(permission)).orElse(false);
    }

    /**
     * Awards a declared task once, or repeatedly when its datapack definition explicitly permits it.
     */
    public static Result completeTask(SectData data, Identifier sectId, SectDefinition definition, Identifier taskId) {
        if (data.sect().filter(sectId::equals).isEmpty()) return Result.rejected(Failure.NOT_MEMBER);
        Task task = definition.tasks().stream().filter(value -> value.id().equals(taskId)).findFirst().orElse(null);
        if (task == null) return Result.rejected(Failure.UNKNOWN_TASK);
        if (task.requiredPermission().isPresent() && !hasPermission(data, sectId, definition, task.requiredPermission().orElseThrow()))
            return Result.rejected(Failure.PERMISSION_DENIED);
        if (task.once() && data.hasCompletedTask(taskId)) return Result.rejected(Failure.TASK_ALREADY_COMPLETED);
        if (task.once()) data.completeTask(taskId);
        data.addContribution(task.contributionReward());
        return Result.changedResult();
    }

    /**
     * Validates all costs before deducting contribution and returns concrete item IDs for a server-owned inventory insertion.
     */
    public static ExchangeResult exchange(SectData data, Identifier sectId, SectDefinition definition, Identifier exchangeId,
                                          ResourceHolderData resources, FormulaContext context) {
        if (data.sect().filter(sectId::equals).isEmpty()) return ExchangeResult.rejected(Failure.NOT_MEMBER);
        Exchange exchange = definition.exchanges().stream().filter(value -> value.id().equals(exchangeId)).findFirst().orElse(null);
        if (exchange == null) return ExchangeResult.rejected(Failure.UNKNOWN_EXCHANGE);
        if (exchange.requiredPermission().isPresent() && !hasPermission(data, sectId, definition, exchange.requiredPermission().orElseThrow()))
            return ExchangeResult.rejected(Failure.PERMISSION_DENIED);
        if (data.contribution() < exchange.contributionCost())
            return ExchangeResult.rejected(Failure.INSUFFICIENT_CONTRIBUTION);
        Evaluation costs;
        try {
            costs = ResourceTransactions.evaluate(exchange.costs(), context);
        } catch (IllegalArgumentException exception) {
            return ExchangeResult.rejected(Failure.INVALID_COST);
        }
        ResourceTransactions.Result payment = ResourceTransactions.tryConsume(resources, costs);
        if (!payment.committed()) return ExchangeResult.rejected(Failure.INSUFFICIENT_RESOURCE);
        if (!data.consumeContribution(exchange.contributionCost()))
            throw new IllegalStateException("Contribution changed during sect exchange");
        return ExchangeResult.completed(exchange.outputs());
    }

    /**
     * Claims an unowned chunk for the member's sect after the rank permission is verified.
     */
    public static Result claimTerritory(SectData data, Identifier sectId, SectDefinition definition, SectTerritoryData territory, Identifier permission) {
        if (!definition.territoryPermissions().contains(permission) || !hasPermission(data, sectId, definition, permission))
            return Result.rejected(Failure.PERMISSION_DENIED);
        if (territory.claimed()) return Result.rejected(Failure.TERRITORY_CLAIMED);
        territory.claim(sectId);
        return Result.changedResult();
    }

    /**
     * A territory permits only its owning sect and its rank's declared permission.
     */
    public static boolean canUseTerritory(SectData data, Identifier sectId, SectDefinition definition, SectTerritoryData territory, Identifier permission) {
        return territory.owner().filter(sectId::equals).isPresent() && hasPermission(data, sectId, definition, permission);
    }

    /**
     * Releases a claimed chunk only for the owning sect and an explicitly authorised rank.
     */
    public static Result releaseTerritory(SectData data, Identifier sectId, SectDefinition definition, SectTerritoryData territory, Identifier permission) {
        if (territory.owner().filter(sectId::equals).isEmpty()) return Result.rejected(Failure.NOT_TERRITORY_OWNER);
        if (!definition.territoryPermissions().contains(permission) || !hasPermission(data, sectId, definition, permission))
            return Result.rejected(Failure.PERMISSION_DENIED);
        territory.clear();
        return Result.changedResult();
    }

    public enum Failure {DISABLED, ALREADY_MEMBER, NOT_MEMBER, CANCELLED, INVALID_CONTRIBUTION, INVALID_RANK, NO_PROMOTION, INVALID_COST, INSUFFICIENT_RESOURCE, UNKNOWN_TASK, TASK_ALREADY_COMPLETED, UNKNOWN_EXCHANGE, PERMISSION_DENIED, INSUFFICIENT_CONTRIBUTION, TERRITORY_CLAIMED, NOT_TERRITORY_OWNER}

    public record Result(boolean changed, Failure failure) {
        static Result changedResult() {
            return new Result(true, null);
        }

        static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }

    public record ExchangeResult(boolean changed, Failure failure, List<Identifier> outputs) {
        static ExchangeResult completed(List<Identifier> outputs) {
            return new ExchangeResult(true, null, List.copyOf(outputs));
        }

        static ExchangeResult rejected(Failure failure) {
            return new ExchangeResult(false, failure, List.of());
        }
    }
}
