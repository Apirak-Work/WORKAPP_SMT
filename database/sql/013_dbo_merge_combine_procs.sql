SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE OR ALTER PROCEDURE dbo.usp_api_get_merge_candidates
    @Runcard varchar(30)
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @HostWo varchar(20);

    SELECT TOP (1) @HostWo = WO
    FROM dbo.Runcard_Detail
    WHERE RUNCARD = @Runcard
      AND ISNULL(FLAG, '') = '1'
    ORDER BY ID DESC;

    IF @HostWo IS NULL
        THROW 51509, 'Host runcard was not found or is not active.', 1;

    SELECT
        RUNCARD AS runcard,
        WO AS workOrder,
        MATERIAL AS material,
        START_WC AS workCenter,
        CAST(ISNULL(QTY, 0) AS int) AS qty,
        CAST(0 AS bit) AS crossWo
    FROM dbo.Runcard_Detail
    WHERE ISNULL(FLAG, '') = '1'
      AND RUNCARD <> @Runcard
      AND WO = @HostWo
    ORDER BY RUNCARD;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_api_get_combine_candidates
    @Runcard varchar(30)
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @HostWo varchar(20);

    SELECT TOP (1) @HostWo = WO
    FROM dbo.Runcard_Detail
    WHERE RUNCARD = @Runcard
      AND ISNULL(FLAG, '') = '1'
    ORDER BY ID DESC;

    IF @HostWo IS NULL
        THROW 51509, 'Host runcard was not found or is not active.', 1;

    SELECT
        RUNCARD AS runcard,
        WO AS workOrder,
        MATERIAL AS material,
        START_WC AS workCenter,
        CAST(ISNULL(QTY, 0) AS int) AS qty,
        CAST(CASE WHEN ISNULL(WO, '') <> ISNULL(@HostWo, '') THEN 1 ELSE 0 END AS bit) AS crossWo
    FROM dbo.Runcard_Detail
    WHERE ISNULL(FLAG, '') = '1'
      AND RUNCARD <> @Runcard
    ORDER BY crossWo DESC, WO, RUNCARD;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_api_merge_combine_runcards
    @HostRuncard varchar(30),
    @SourceRcs varchar(max),
    @OperatorId varchar(50),
    @ActionType varchar(10)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @Action varchar(10) = UPPER(LTRIM(RTRIM(ISNULL(@ActionType, ''))));
    DECLARE @Now datetime = GETDATE();

    IF @Action NOT IN ('MERGE', 'COMBINE')
        THROW 51500, 'ActionType must be MERGE or COMBINE.', 1;

    DECLARE @Sources TABLE
    (
        RUNCARD varchar(30) NOT NULL PRIMARY KEY
    );

    DECLARE @Xml xml =
        TRY_CAST('<x>' + REPLACE(REPLACE(ISNULL(@SourceRcs, ''), '&', '&amp;'), ',', '</x><x>') + '</x>' AS xml);

    INSERT INTO @Sources (RUNCARD)
    SELECT DISTINCT LTRIM(RTRIM(T.c.value('.', 'varchar(30)')))
    FROM @Xml.nodes('/x') AS T(c)
    WHERE LTRIM(RTRIM(T.c.value('.', 'varchar(30)'))) <> '';

    IF NOT EXISTS (SELECT 1 FROM @Sources)
        THROW 51500, 'At least one source runcard is required.', 1;

    IF EXISTS (SELECT 1 FROM @Sources WHERE RUNCARD = @HostRuncard)
        THROW 51508, 'Host runcard cannot be included in sources.', 1;

    -- Spec validations (MERGE_VALIDATION_FAILED range)
    IF LEN(ISNULL(@HostRuncard, '')) = 0 OR LEN(@HostRuncard) > 10
        THROW 51501, 'Host runcard is required and must be <= 10 characters.', 1;

    IF LEN(ISNULL(@OperatorId, '')) = 0 OR LEN(@OperatorId) > 6
        THROW 51502, 'Operator id is required and must be <= 6 characters.', 1;

    IF NOT EXISTS (SELECT 1 FROM dbo.USERLOGIN WHERE USER_EN = @OperatorId)
        THROW 51503, 'Operator id was not found in USERLOGIN.', 1;

    BEGIN TRANSACTION;

    BEGIN TRY
        DECLARE
            @HostId int,
            @HostWo varchar(20),
            @HostMaterial varchar(50),
            @HostWc varchar(10),
            @HostOperation varchar(5),
            @HostQtyBefore int,
            @SourceQtyTotal int,
            @ResultQty int;

        SELECT TOP (1)
            @HostId = ID,
            @HostWo = WO,
            @HostMaterial = MATERIAL,
            @HostWc = START_WC,
            @HostQtyBefore = CAST(ISNULL(QTY, 0) AS int)
        FROM dbo.Runcard_Detail WITH (UPDLOCK, HOLDLOCK)
        WHERE RUNCARD = @HostRuncard
          AND ISNULL(FLAG, '') = '1'
        ORDER BY ID DESC;

        IF @HostId IS NULL
            THROW 51509, 'Host runcard was not found or is not active.', 1;

        DECLARE @LockedSources TABLE
        (
            ID int NOT NULL,
            RUNCARD varchar(30) NOT NULL PRIMARY KEY,
            WO varchar(20) NULL,
            MATERIAL varchar(50) NULL,
            START_WC varchar(10) NULL,
            QTY int NOT NULL
        );

        INSERT INTO @LockedSources (ID, RUNCARD, WO, MATERIAL, START_WC, QTY)
        SELECT rd.ID, rd.RUNCARD, rd.WO, rd.MATERIAL, rd.START_WC, CAST(ISNULL(rd.QTY, 0) AS int)
        FROM dbo.Runcard_Detail rd WITH (UPDLOCK, HOLDLOCK)
        INNER JOIN @Sources s ON s.RUNCARD = rd.RUNCARD
        WHERE ISNULL(rd.FLAG, '') = '1';

        IF (SELECT COUNT(*) FROM @LockedSources) <> (SELECT COUNT(*) FROM @Sources)
            THROW 51510, 'One or more source runcards were not found or are not active.', 1;

        IF EXISTS (SELECT 1 FROM @LockedSources WHERE QTY <= 0)
            THROW 51512, 'All source runcards must have quantity greater than zero.', 1;

        IF EXISTS (SELECT 1 FROM @LockedSources WHERE ISNULL(MATERIAL, '') <> ISNULL(@HostMaterial, ''))
            THROW 51512, 'Host and source runcards must have the same material.', 1;

        IF EXISTS (SELECT 1 FROM @LockedSources WHERE ISNULL(START_WC, '') <> ISNULL(@HostWc, ''))
            THROW 51512, 'Host and source runcards must be at the same work center.', 1;

        IF @Action = 'MERGE'
           AND EXISTS (SELECT 1 FROM @LockedSources WHERE ISNULL(WO, '') <> ISNULL(@HostWo, ''))
            THROW 51511, 'MERGE requires all runcards to share the same work order.', 1;

        SELECT @SourceQtyTotal = SUM(QTY)
        FROM @LockedSources;

        SET @ResultQty = @HostQtyBefore + ISNULL(@SourceQtyTotal, 0);

        SELECT TOP (1) @HostOperation = OPERATION
        FROM dbo.RC_Transection
        WHERE RUNCARD = @HostRuncard
          AND ISNULL(CANC_FLAG, '') <> 'X'
        ORDER BY ID DESC;

        UPDATE dbo.Runcard_Detail
        SET FLAG = 'X',
            OLD_QTY = @HostQtyBefore,
            CBY = @OperatorId
        WHERE ID = @HostId;

        INSERT INTO dbo.Runcard_Detail
            (PLANT, WO, MATERIAL, RC_TYPE, RUNCARD, ASSY_LOT,
             ASSY_LOT_FULL, LOT_TYPE, LOT_GROUP, TEST_LOT, SEMI_PART,
             PART_ID, DATE_CODE, WAFER_FAB, QTY, OLD_QTY, MOTHER,
             MOTHER_QTY, MODEL, MARKING1, MARKING2, MI_TEST,
             CUST_LOT_NUM, SRC_LOT_NUMBER, TSMSpec, BSMSpec, FLAG,
             WC_HOLD, CONF_FIN, CONF_YIELD, CONF_SCRAP, GR_LOCATION,
             GR_FIN, CDATE, CBY, B2B_STATUS, SRC_LOT_QTY,
             FLAG_COMBINE, START_WC, CONFFINDATE, EXCEPTION)
        SELECT
             PLANT, WO, MATERIAL, RC_TYPE, RUNCARD, ASSY_LOT,
             ASSY_LOT_FULL, LOT_TYPE, LOT_GROUP, TEST_LOT, SEMI_PART,
             PART_ID, DATE_CODE, WAFER_FAB, @ResultQty, @HostQtyBefore, MOTHER,
             MOTHER_QTY, MODEL, MARKING1, MARKING2, MI_TEST,
             CUST_LOT_NUM, SRC_LOT_NUMBER, TSMSpec, BSMSpec, '1',
             WC_HOLD, CONF_FIN, CONF_YIELD, CONF_SCRAP, GR_LOCATION,
             GR_FIN, @Now, @OperatorId, B2B_STATUS, SRC_LOT_QTY,
             CASE WHEN @Action = 'COMBINE' THEN '1' ELSE FLAG_COMBINE END,
             START_WC, CONFFINDATE, EXCEPTION
        FROM dbo.Runcard_Detail
        WHERE ID = @HostId;

        UPDATE rd
        SET rd.FLAG = 'X',
            rd.OLD_QTY = ls.QTY,
            rd.FLAG_COMBINE = CASE WHEN @Action = 'COMBINE' THEN '1' ELSE rd.FLAG_COMBINE END,
            rd.CBY = @OperatorId
        FROM dbo.Runcard_Detail rd
        INNER JOIN @LockedSources ls ON ls.ID = rd.ID;

        UPDATE rt
        SET FLAG_MERGE = 'X',
            MERGE_FROM = LEFT(@SourceRcs, 250),
            UDATE = @Now,
            CBY = @OperatorId
        FROM dbo.RC_Transection rt
        WHERE (rt.RUNCARD = @HostRuncard OR EXISTS (SELECT 1 FROM @LockedSources ls WHERE ls.RUNCARD = rt.RUNCARD))
          AND ISNULL(rt.CANC_FLAG, '') <> 'X';

        INSERT INTO dbo.RUNCARD_HISTORY
            (RCTYPE, CHILDWO, CHILDRUNCARD, CHILDQTY,
             MOTHERWO, MOTHERRUNCARD, MOTHERQTY, SUMQTY,
             WORKCENTER, OPERATION, CBY, CDATE)
        SELECT
            @Action,
            ls.WO,
            ls.RUNCARD,
            ls.QTY,
            @HostWo,
            @HostRuncard,
            @HostQtyBefore,
            @ResultQty,
            @HostWc,
            @HostOperation,
            @OperatorId,
            @Now
        FROM @LockedSources ls;

        COMMIT TRANSACTION;

        SELECT
            @HostRuncard AS hostRuncard,
            @ResultQty AS resultQty,
            @HostQtyBefore AS hostQtyBefore,
            @SourceQtyTotal AS sourceQtyTotal,
            @HostWo AS workOrder,
            @HostWc AS workCenter,
            @HostOperation AS operation,
            @Action AS actionType,
            @OperatorId AS operatorId,
            @Now AS mergedAt,
            'OK' AS status,
            ls.RUNCARD AS sourceRuncard,
            ls.WO AS sourceWorkOrder,
            ls.QTY AS sourceQty
        FROM @LockedSources ls
        ORDER BY ls.RUNCARD;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK TRANSACTION;

        THROW;
    END CATCH;
END;
GO
