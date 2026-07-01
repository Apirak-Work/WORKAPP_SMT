using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace StarsOne.Api.FactorySchema;

/// <summary>
/// Maps the core Runcard_Detail table used to identify the current lot state.
/// </summary>
[Table("Runcard_Detail")]
public sealed class RuncardDetail
{
    [Key]
    [Column("ID")]
    public int Id { get; set; }

    [Column("PLANT")]
    public string? Plant { get; set; }

    [Column("WO")]
    public string? Wo { get; set; }

    [Column("MATERIAL")]
    public string? Material { get; set; }

    [Column("RC_TYPE")]
    public string? RcType { get; set; }

    [Column("RUNCARD")]
    public string? Runcard { get; set; }

    [Column("ASSY_LOT")]
    public string? AssyLot { get; set; }

    [Column("DATE_CODE")]
    public string? DateCode { get; set; }

    [Column("QTY")]
    public decimal Qty { get; set; }

    [Column("FLAG")]
    public string? Flag { get; set; }

    [Column("START_WC")]
    public string? StartWc { get; set; }

    [Column("CONFIRM_DONE")]
    public string? ConfirmDone { get; set; }
}

/// <summary>
/// Maps the RC_Transection table used as the production confirmation log.
/// </summary>
[Table("RC_Transection")]
public sealed class RcTransaction
{
    [Key]
    [Column("ID")]
    public int Id { get; set; }

    [Column("PLANT")]
    public string? Plant { get; set; }

    [Column("ORDERID")]
    public string? OrderId { get; set; }

    [Column("ROUTING_NO")]
    public string? RoutingNo { get; set; }

    [Column("MATERIAL")]
    public string? Material { get; set; }

    [Column("MATERIAL_DESC")]
    public string? MaterialDesc { get; set; }

    [Column("RUNCARD")]
    public string? Runcard { get; set; }

    [Column("WORK_CENTER")]
    public string? WorkCenter { get; set; }

    [Column("RECEIVE_QTY")]
    public decimal ReceiveQty { get; set; }

    [Column("YIELD")]
    public decimal Yield { get; set; }

    [Column("SCRAP")]
    public decimal Scrap { get; set; }

    [Column("POSTG_DATE")]
    public DateTimeOffset? PostgDate { get; set; }
}
